"""验证兼容模型服务地址的配置与传递；不调用真实模型。"""

# 用临时配置和替身捕获构造参数，验证模型地址、密钥参数及兼容选项如何传入客户端。

from types import SimpleNamespace

import langchain.agents
import langchain_openai

from app.agent import runtime
from app.core.config import Settings


def test_settings_loads_model_endpoint_from_env_file(tmp_path, monkeypatch):
    # 1. 清除可能覆盖文件的环境变量，再在临时目录创建测试专用 .env。
    monkeypatch.delenv('OPENAI_BASE_URL', raising=False)
    env_file = tmp_path / '.env'
    env_file.write_text('OPENAI_BASE_URL=https://api.deepseek.com\n', encoding='utf-8')

    # 2. 读取临时文件，确认配置字段收到正确地址；不改开发者自己的 .env。
    config = Settings(_env_file=env_file)

    assert config.openai_base_url == 'https://api.deepseek.com'


def test_factory_passes_deepseek_endpoint_and_disables_thinking(monkeypatch):
    # 替换模型构造器和 Agent 工厂，只检查参数，不发生真实模型 API 调用。
    captured = {}
    model = object()
    agent = object()

    def build_model(**kwargs):
        captured.update(kwargs)
        return model

    monkeypatch.setattr(runtime, 'settings', SimpleNamespace(
        model_name='deepseek-flash',
        openai_api_key='test-key',
        openai_base_url='https://api.deepseek.com',
    ))
    monkeypatch.setattr(langchain_openai, 'ChatOpenAI', build_model)
    monkeypatch.setattr(langchain.agents, 'create_agent', lambda **kwargs: agent)

    assert runtime.create_marketplace_agent([]) is agent
    assert captured['base_url'] == 'https://api.deepseek.com'
    assert captured['extra_body'] == {'thinking': {'type': 'disabled'}}


def test_factory_keeps_default_endpoint_when_no_url_is_configured(monkeypatch):
    # 没配置自定义地址时交给模型适配器使用默认地址，不附加 DeepSeek 专用选项。
    captured = {}

    def build_model(**kwargs):
        captured.update(kwargs)
        return object()

    monkeypatch.setattr(runtime, 'settings', SimpleNamespace(
        model_name='test-model',
        openai_api_key='test-key',
        openai_base_url=None,
    ))
    monkeypatch.setattr(langchain_openai, 'ChatOpenAI', build_model)
    monkeypatch.setattr(langchain.agents, 'create_agent', lambda **kwargs: object())

    runtime.create_marketplace_agent([])

    assert captured['base_url'] is None
    assert 'extra_body' not in captured
