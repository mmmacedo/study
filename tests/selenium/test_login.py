"""
Testes Selenium — fluxo de login e autenticação OAuth2 (Keycloak PKCE).

Pré-condições:
  - frontend em localhost:3000  (npm run dev)
  - api-gateway em localhost:8080
  - auth-service em localhost:8081
  - keycloak em localhost:8180

Os testes 3–6 (login_success → logout) compartilham a mesma sessão do driver
para evitar múltiplos logins. A ordem de execução é garantida pela numeração
nos nomes das funções.
"""

import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from urllib.parse import urlencode

from conftest import BASE_URL, TEST_USER, TEST_ADMIN, WRONG_PASS, login, wait_for_url

KEYCLOAK_AUTH_URL = "http://localhost:8180/realms/study/protocol/openid-connect/auth"


class TestLoginPage:
    """Testa o estado inicial da aplicação (sem autenticação)."""

    def test_01_login_page_loads(self, driver):
        """A landing page carrega e exibe o botão de login."""
        driver.get(BASE_URL)
        btn = driver.find_element(By.CSS_SELECTOR, '[data-testid="login-button"]')
        assert btn.is_displayed()
        assert "Keycloak" in btn.text

    def test_02_login_redirects_to_keycloak(self, driver):
        """Clicar em 'Entrar' redireciona para a tela de login do Keycloak."""
        driver.get(BASE_URL)
        driver.find_element(By.CSS_SELECTOR, '[data-testid="login-button"]').click()
        wait_for_url(driver, "8180")
        assert "8180" in driver.current_url


class TestLoginFlow:
    """Testa o fluxo completo de login → dashboard → logout com testuser."""

    def test_03_login_success(self, driver):
        """Login com credenciais válidas redireciona para o dashboard."""
        login(driver, **TEST_USER)
        assert "/dashboard" in driver.current_url

    def test_04_dashboard_shows_username(self, driver):
        """Dashboard exibe o nome de usuário do token."""
        el = driver.find_element(By.CSS_SELECTOR, '[data-testid="username-display"]')
        assert el.text == TEST_USER["username"]

    def test_05_dashboard_shows_role_user(self, driver):
        """Dashboard exibe a role USER atribuída ao testuser no Keycloak."""
        el = driver.find_element(By.CSS_SELECTOR, '[data-testid="roles-display"]')
        assert "USER" in el.text

    def test_06_logout_returns_to_home(self, driver):
        """Clicar em 'Sair' limpa a sessão e retorna à landing page."""
        driver.find_element(By.CSS_SELECTOR, '[data-testid="logout-button"]').click()
        # Keycloak end_session redirect pode demorar (vai a :8180 e volta a :3000)
        wait_for_url(driver, "localhost:3000", timeout=30)
        # Botão de login visível novamente confirma que a sessão foi encerrada
        btn = WebDriverWait(driver, 20).until(
            EC.visibility_of_element_located(
                (By.CSS_SELECTOR, '[data-testid="login-button"]')
            )
        )
        assert btn.is_displayed()


class TestAdminLogin:
    """Testa login com testadmin que possui roles USER + ADMIN."""

    def test_07_admin_login_shows_admin_role(self, driver):
        """testadmin deve ter role ADMIN visível no dashboard."""
        login(driver, **TEST_ADMIN)
        el = driver.find_element(By.CSS_SELECTOR, '[data-testid="roles-display"]')
        assert "ADMIN" in el.text
        # Faz logout para não poluir testes subsequentes.
        # Mesmo padrão do test_06: wait_for_url separa o tempo do redirect Keycloak
        # do tempo de render do botão — evita timeout quando o redirect demora ~25s.
        driver.find_element(By.CSS_SELECTOR, '[data-testid="logout-button"]').click()
        wait_for_url(driver, "localhost:3000", timeout=30)
        WebDriverWait(driver, 20).until(
            EC.visibility_of_element_located(
                (By.CSS_SELECTOR, '[data-testid="login-button"]')
            )
        )


class TestLoginFailure:
    """Testa comportamento com credenciais inválidas."""

    def test_08_wrong_password_stays_on_keycloak(self, driver):
        """
        Credenciais incorretas mantêm o usuário na tela de login do Keycloak
        com mensagem de erro — o redirecionamento de volta ao frontend NÃO ocorre.

        Navega diretamente ao Keycloak com prompt=login (força exibição do form
        independentemente do estado SSO residual do test_07 — mais robusto que
        tentar limpar cookies em headless Chrome).
        """
        params = urlencode({
            "client_id":     "study-api",
            "redirect_uri":  "http://localhost:3000/callback",
            "response_type": "code",
            "scope":         "openid",
            "prompt":        "login",
        })
        driver.get(f"{KEYCLOAK_AUTH_URL}?{params}")

        WebDriverWait(driver, 15).until(
            EC.presence_of_element_located((By.ID, "username"))
        )

        driver.find_element(By.ID, "username").send_keys(WRONG_PASS["username"])
        driver.find_element(By.ID, "password").send_keys(WRONG_PASS["password"])
        driver.find_element(By.ID, "kc-login").click()

        # Keycloak 26.x usa #input-error-username (PatternFly 5).
        # O seletor [id^='input-error'] cobre variantes futuras do tema.
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located(
                (By.CSS_SELECTOR, "[id^='input-error'], .alert-error")
            )
        )
        assert "8180" in driver.current_url
        assert "/dashboard" not in driver.current_url
