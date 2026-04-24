"""
Fixtures e helpers compartilhados pelos testes Selenium.

Requisitos para rodar:
  - Google Chrome instalado
  - frontend rodando em localhost:3000  (npm run dev)
  - Stack Docker rodando: postgres, keycloak, auth-service, api-gateway
    (docker compose up -d postgres keycloak auth-service api-gateway)
"""

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

BASE_URL = "http://localhost:3000"

# Usuários de teste pré-configurados no realm Keycloak 'study'
TEST_USER     = {"username": "testuser",  "password": "Test1234!"}
TEST_ADMIN    = {"username": "testadmin", "password": "Test1234!"}
WRONG_PASS    = {"username": "testuser",  "password": "senha_errada"}


@pytest.fixture(scope="session")
def driver():
    """
    WebDriver Chrome com escopo de sessão: uma única instância para todos os testes.

    --headless=new: modo headless de geração mais recente (melhor compatibilidade).
    --no-sandbox + --disable-dev-shm-usage: obrigatório em ambientes CI/Linux.
    Selenium Manager (embutido no Selenium 4.6+) baixa o chromedriver automaticamente.
    """
    options = webdriver.ChromeOptions()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--window-size=1280,800")

    # Selenium Manager (embutido desde Selenium 4.6) baixa e gerencia o
    # chromedriver automaticamente — sem dependência externa.
    driver = webdriver.Chrome(options=options)
    driver.implicitly_wait(10)
    yield driver
    driver.quit()


def wait_for_url(driver, partial_url: str, timeout: int = 15) -> None:
    """Aguarda até que a URL atual contenha partial_url."""
    WebDriverWait(driver, timeout).until(EC.url_contains(partial_url))


def _clear_keycloak_cookies(driver) -> None:
    """
    Limpa todos os cookies do browser via CDP (Chrome DevTools Protocol).

    Necessário antes de testes que precisam de estado SSO limpo. Cookies SSO
    residuais do Keycloak (KEYCLOAK_SESSION, AUTH_SESSION_ID) causam auto-login
    silencioso — o Keycloak redireciona de volta ao frontend sem exibir o form
    de credenciais, quebrando os testes que esperam o form em :8180.

    Usar Network.clearBrowserCookies (CDP) em vez de navegar para :8180 e chamar
    delete_all_cookies(): a navegação para :8180 pode criar novas sessões
    inadvertidamente antes da limpeza.

    Best-effort: falha silenciosamente se o CDP não estiver disponível.
    """
    try:
        driver.execute_cdp_cmd("Network.clearBrowserCookies", {})
    except Exception:
        pass


def login(driver, username: str, password: str) -> None:
    """
    Executa o fluxo completo de login via Keycloak:
      1. Abre a landing page do frontend
      2. Clica em "Entrar com Keycloak"
      3. Preenche as credenciais na tela de login nativa do Keycloak
      4. Aguarda o redirecionamento de volta ao dashboard

    IDs do form do Keycloak (#username, #password, #kc-login) são os nomes
    padrão gerados pelo tema Keycloak — estáveis entre versões minor.
    """
    driver.get(BASE_URL)
    driver.find_element(By.CSS_SELECTOR, '[data-testid="login-button"]').click()

    # Aguarda o form de login do Keycloak — espera explícita porque o Keycloak
    # pode redirecionar rapidamente se ainda houver sessão SSO residual.
    WebDriverWait(driver, 15).until(
        EC.presence_of_element_located((By.ID, "username"))
    )

    driver.find_element(By.ID, "username").send_keys(username)
    driver.find_element(By.ID, "password").send_keys(password)
    driver.find_element(By.ID, "kc-login").click()

    # Aguarda redirecionamento de volta ao dashboard
    wait_for_url(driver, "/dashboard")
