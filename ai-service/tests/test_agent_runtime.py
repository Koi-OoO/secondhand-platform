# 测试 Agent 主流程：用假的模型和 Java 客户端验证结果，不调用真实模型或数据库。
# 每个测试按“准备请求 → 执行 run_agent → assert 检查结果”的顺序阅读。
import pytest

from app.agent.marketplace.schemas import UserContext
from app.agent.state import AgentChatRequest
from app.agent.runtime import AgentUnavailableError, run_agent


class FakeAgent:
    # 模拟一次“模型要求查询商品，再生成回答”的过程，替代真实 Agent。
    def __init__(self, tools):
        self.tools = tools

    async def ainvoke(self, payload):
        # 先执行注册的搜索工具，卡片由工具写入执行上下文；回答文字则单独返回。
        await self.tools[0].ainvoke({"keyword": "台灯"})
        return {"messages": [{"role": "assistant", "content": "找到 1 件商品"}]}


class FakeClient:
    async def search_listings(self, **kwargs):
        from app.agent.marketplace.schemas import ProductCard

        return [ProductCard(productId=10, title="台灯", price=89, stock=1, conditionLevel=2, tradeLocation="主校区", detailPath="/product/10")]


class EmptyClient:
    async def search_listings(self, **kwargs):
        return []


class EmptyAgent:
    def __init__(self, tools):
        self.tools = tools

    async def ainvoke(self, payload):
        await self.tools[0].ainvoke({"keyword": "不存在的商品"})
        return {"messages": [{"role": "assistant", "content": "没有找到匹配的商品。"}]}


@pytest.mark.asyncio
async def test_run_agent_returns_answer_and_live_items() -> None:
    # 1. 构造 Java 会传入的请求，包括当前问题和已登录用户身份。
    request = AgentChatRequest(
        conversationId="c-1",
        message="找台灯",
        userContext=UserContext(userId=7, campus="东校区", role="USER"),
        traceId="trace-1",
    )

    # 2. 注入替身执行主流程；lambda 是接收 tools 并创建 FakeAgent 的简短函数。
    result = await run_agent(request, client=FakeClient(), agent_factory=lambda tools: FakeAgent(tools))

    # 3. 同时检查文字回答和真实工具卡片，不能只检查模型文字。
    assert result.answer == "找到 1 件商品"
    assert result.items[0].product_id == 10


@pytest.mark.asyncio
async def test_write_request_is_rejected_without_agent_call() -> None:
    request = AgentChatRequest(
        conversationId="c-1",
        message="帮我下单",
        userContext=UserContext(userId=7, campus="东校区", role="USER"),
        traceId="trace-1",
    )

    def fail_factory(tools):
        # 如果写操作误进入 Agent 创建流程，直接让测试失败。
        raise AssertionError("write request must not create an agent")

    result = await run_agent(request, client=FakeClient(), agent_factory=fail_factory)
    assert "暂不支持" in result.answer
    assert result.items == []


@pytest.mark.asyncio
async def test_run_agent_keeps_items_empty_when_java_returns_no_results() -> None:
    # 无匹配商品时保留空列表，不能用模型文字凑出虚构商品。
    request = AgentChatRequest(
        conversationId="c-1",
        message="找不存在的商品",
        userContext=UserContext(userId=7, campus="东校区", role="USER"),
        traceId="trace-1",
    )

    result = await run_agent(request, client=EmptyClient(), agent_factory=lambda tools: EmptyAgent(tools))

    assert result.answer == "没有找到匹配的商品。"
    assert result.items == []


@pytest.mark.asyncio
async def test_missing_model_is_a_stable_unavailable_error(monkeypatch) -> None:
    # 明确设置缺失模型的前提，避免测试读取开发者 .env 中的真实模型。
    monkeypatch.setattr('app.agent.runtime.settings.model_name', None)
    request = AgentChatRequest(
        conversationId="c-1",
        message="找台灯",
        userContext=UserContext(userId=7, campus="东校区", role="USER"),
        traceId="trace-1",
    )

    with pytest.raises(AgentUnavailableError):
        await run_agent(request, client=FakeClient(), agent_factory=None, model_name=None)
