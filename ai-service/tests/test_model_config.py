"""验证兼容模型服务地址的配置与传递；不调用真实模型。"""

from types import SimpleNamespace

import langchain.agents
import langchain_openai

from app.agent import runtime
from app.core.config import Settings


def test_settings_loads_model_endpoint_from_env_file(tmp_path, monkeypatch):
    monkeypatch.delenv('OPENAI_BASE_URL', raising=False)
    env_file = tmp_path / '.env'
    env_file.write_text('OPENAI_BASE_URL=https://api.deepseek.com\n', encoding='utf-8')

    config = Settings(_env_file=env_file)

    assert config.openai_base_url == 'https://api.deepseek.com'


def test_factory_passes_deepseek_endpoint_and_disables_thinking(monkeypatch):
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
