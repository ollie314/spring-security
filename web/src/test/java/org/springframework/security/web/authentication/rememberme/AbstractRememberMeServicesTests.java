package org.springframework.security.web.authentication.rememberme;

import static org.fest.assertions.Assertions.*;
import static org.powermock.api.mockito.PowerMockito.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.PrepareOnlyThisForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Luke Taylor
 */
@SuppressWarnings("unchecked")
@RunWith(PowerMockRunner.class)
@PrepareOnlyThisForTest(ReflectionUtils.class)
public class AbstractRememberMeServicesTests {
	static User joe = new User("joe", "password", true, true, true, true,
			AuthorityUtils.createAuthorityList("ROLE_A"));

	MockUserDetailsService uds;

	@Before
	public void setup() {
		uds = new MockUserDetailsService(joe, false);
	}

	@Test(expected = InvalidCookieException.class)
	public void nonBase64CookieShouldBeDetected() {
		new MockRememberMeServices(uds).decodeCookie("nonBase64CookieValue%");
	}

	@Test
	public void setAndGetAreConsistent() throws Exception {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		assertNotNull(services.getCookieName());
		assertNotNull(services.getParameter());
		assertEquals("xxxx", services.getKey());
		services.setParameter("rm");
		assertEquals("rm", services.getParameter());
		services.setCookieName("kookie");
		assertEquals("kookie", services.getCookieName());
		services.setTokenValiditySeconds(600);
		assertEquals(600, services.getTokenValiditySeconds());
		assertSame(uds, services.getUserDetailsService());
		AuthenticationDetailsSource ads = mock(AuthenticationDetailsSource.class);
		services.setAuthenticationDetailsSource(ads);
		assertSame(ads, services.getAuthenticationDetailsSource());
		services.afterPropertiesSet();
	}

	@Test
	public void cookieShouldBeCorrectlyEncodedAndDecoded() throws Exception {
		String[] cookie = new String[] { "name", "cookie", "tokens", "blah" };
		MockRememberMeServices services = new MockRememberMeServices(uds);

		String encoded = services.encodeCookie(cookie);
		// '=' aren't allowed in version 0 cookies.
		assertFalse(encoded.endsWith("="));
		String[] decoded = services.decodeCookie(encoded);

		assertEquals(4, decoded.length);
		assertEquals("name", decoded[0]);
		assertEquals("cookie", decoded[1]);
		assertEquals("tokens", decoded[2]);
		assertEquals("blah", decoded[3]);
	}

	@Test
	public void cookieWithOpenIDidentifierAsNameIsEncodedAndDecoded() throws Exception {
		String[] cookie = new String[] { "http://id.openid.zz", "cookie", "tokens",
				"blah" };
		MockRememberMeServices services = new MockRememberMeServices(uds);

		String[] decoded = services.decodeCookie(services.encodeCookie(cookie));
		assertEquals(4, decoded.length);
		assertEquals("http://id.openid.zz", decoded[0]);

		// Check https (SEC-1410)
		cookie[0] = "https://id.openid.zz";
		decoded = services.decodeCookie(services.encodeCookie(cookie));
		assertEquals(4, decoded.length);
		assertEquals("https://id.openid.zz", decoded[0]);
	}

	@Test
	public void autoLoginShouldReturnNullIfNoLoginCookieIsPresented() {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertNull(services.autoLogin(request, response));

		// shouldn't try to invalidate our cookie
		assertNull(response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY));

		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		// set non-login cookie
		request.setCookies(new Cookie("mycookie", "cookie"));
		assertNull(services.autoLogin(request, response));
		assertNull(response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY));
	}

	@Test
	public void successfulAutoLoginReturnsExpectedAuthentication() throws Exception {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		services.afterPropertiesSet();
		assertNotNull(services.getUserDetailsService());

		MockHttpServletRequest request = new MockHttpServletRequest();

		request.setCookies(createLoginCookie("cookie:1:2"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		Authentication result = services.autoLogin(request, response);

		assertNotNull(result);
	}

	@Test
	public void autoLoginShouldFailIfCookieIsNotBase64() throws Exception {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.setCookies(new Cookie(
				AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, "ZZZ"));
		Authentication result = services.autoLogin(request, response);
		assertNull(result);
		assertCookieCancelled(response);
	}

	@Test
	public void autoLoginShouldFailIfCookieIsEmpty() throws Exception {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.setCookies(new Cookie(
				AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, ""));
		Authentication result = services.autoLogin(request, response);
		assertNull(result);
		assertCookieCancelled(response);
	}

	@Test
	public void autoLoginShouldFailIfInvalidCookieExceptionIsRaised() {
		MockRememberMeServices services = new MockRememberMeServices(
				new MockUserDetailsService(joe, true));

		MockHttpServletRequest request = new MockHttpServletRequest();
		// Wrong number of tokens
		request.setCookies(createLoginCookie("cookie:1"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		Authentication result = services.autoLogin(request, response);

		assertNull(result);

		assertCookieCancelled(response);
	}

	@Test
	public void autoLoginShouldFailIfUserNotFound() {
		uds.setThrowException(true);
		MockRememberMeServices services = new MockRememberMeServices(uds);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(createLoginCookie("cookie:1:2"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		Authentication result = services.autoLogin(request, response);

		assertNull(result);

		assertCookieCancelled(response);
	}

	@Test
	public void autoLoginShouldFailIfUserAccountIsLocked() {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		services.setUserDetailsChecker(new AccountStatusUserDetailsChecker());
		uds.toReturn = new User("joe", "password", false, true, true, true,
				joe.getAuthorities());

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(createLoginCookie("cookie:1:2"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		Authentication result = services.autoLogin(request, response);

		assertNull(result);

		assertCookieCancelled(response);
	}

	@Test
	public void loginFailShouldCancelCookie() {
		uds.setThrowException(true);
		MockRememberMeServices services = new MockRememberMeServices(uds);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("contextpath");
		request.setCookies(createLoginCookie("cookie:1:2"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		services.loginFail(request, response);

		assertCookieCancelled(response);
	}

	@Test
	public void logoutShouldCancelCookie() throws Exception {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("contextpath");
		request.setCookies(createLoginCookie("cookie:1:2"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		services.logout(request, response, mock(Authentication.class));
		// Try again with null Authentication
		response = new MockHttpServletResponse();

		services.logout(request, response, null);

		assertCookieCancelled(response);
	}

	@Test(expected = CookieTheftException.class)
	public void cookieTheftExceptionShouldBeRethrown() {
		MockRememberMeServices services = new MockRememberMeServices(uds) {
			protected UserDetails processAutoLoginCookie(String[] cookieTokens,
					HttpServletRequest request, HttpServletResponse response) {
				throw new CookieTheftException("Pretending cookie was stolen");
			}
		};

		MockHttpServletRequest request = new MockHttpServletRequest();

		request.setCookies(createLoginCookie("cookie:1:2"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		services.autoLogin(request, response);
	}

	@Test
	public void loginSuccessCallsOnLoginSuccessCorrectly() {
		MockRememberMeServices services = new MockRememberMeServices(uds);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		Authentication auth = new UsernamePasswordAuthenticationToken("joe", "password");

		// No parameter set
		services.loginSuccess(request, response, auth);
		assertFalse(services.loginSuccessCalled);

		// Parameter set to true
		services = new MockRememberMeServices(uds);
		request.setParameter(MockRememberMeServices.DEFAULT_PARAMETER, "true");
		services.loginSuccess(request, response, auth);
		assertTrue(services.loginSuccessCalled);

		// Different parameter name, set to true
		services = new MockRememberMeServices(uds);
		services.setParameter("my_parameter");
		request.setParameter("my_parameter", "true");
		services.loginSuccess(request, response, auth);
		assertTrue(services.loginSuccessCalled);

		// Parameter set to false
		services = new MockRememberMeServices(uds);
		request.setParameter(MockRememberMeServices.DEFAULT_PARAMETER, "false");
		services.loginSuccess(request, response, auth);
		assertFalse(services.loginSuccessCalled);

		// alwaysRemember set to true
		services = new MockRememberMeServices(uds);
		services.setAlwaysRemember(true);
		services.loginSuccess(request, response, auth);
		assertTrue(services.loginSuccessCalled);
	}

	@Test
	public void setCookieUsesCorrectNamePathAndValue() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		request.setContextPath("contextpath");
		MockRememberMeServices services = new MockRememberMeServices(uds) {
			protected String encodeCookie(String[] cookieTokens) {
				return cookieTokens[0];
			}
		};
		services.setCookieName("mycookiename");
		services.setCookie(new String[] { "mycookie" }, 1000, request, response);
		Cookie cookie = response.getCookie("mycookiename");

		assertNotNull(cookie);
		assertEquals("mycookie", cookie.getValue());
		assertEquals("mycookiename", cookie.getName());
		assertEquals("contextpath", cookie.getPath());
		assertFalse(cookie.getSecure());
	}

	@Test
	public void setCookieSetsSecureFlagIfConfigured() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		request.setContextPath("contextpath");

		MockRememberMeServices services = new MockRememberMeServices(uds) {
			protected String encodeCookie(String[] cookieTokens) {
				return cookieTokens[0];
			}
		};
		services.setUseSecureCookie(true);
		services.setCookie(new String[] { "mycookie" }, 1000, request, response);
		Cookie cookie = response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
		assertTrue(cookie.getSecure());
	}

	@Test
	public void setHttpOnlyIgnoredForServlet25() throws Exception {
		spy(ReflectionUtils.class);
		when(ReflectionUtils.findMethod(Cookie.class, "setHttpOnly", boolean.class))
				.thenReturn(null);

		MockRememberMeServices services = new MockRememberMeServices(uds);
		assertNull(ReflectionTestUtils.getField(services, "setHttpOnlyMethod"));

		services = new MockRememberMeServices("key", new MockUserDetailsService(joe,
				false));
		assertNull(ReflectionTestUtils.getField(services, "setHttpOnlyMethod"));
	}

	// SEC-2791
	@Test
	public void setCookieMaxAge0VersionSet() {
		MockRememberMeServices services = new MockRememberMeServices();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		services.setCookie(new String[] { "value" }, 0, request, response);

		Cookie cookie = response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
		assertThat(cookie.getVersion()).isEqualTo(1);
	}

	// SEC-2791
	@Test
	public void setCookieMaxAgeNegativeVersionSet() {
		MockRememberMeServices services = new MockRememberMeServices();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		services.setCookie(new String[] { "value" }, -1, request, response);

		Cookie cookie = response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
		assertThat(cookie.getVersion()).isEqualTo(1);
	}

	// SEC-2791
	@Test
	public void setCookieMaxAge1VersionSet() {
		MockRememberMeServices services = new MockRememberMeServices();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		services.setCookie(new String[] { "value" }, 1, request, response);

		Cookie cookie = response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
		assertThat(cookie.getVersion()).isEqualTo(0);
	}

	private Cookie[] createLoginCookie(String cookieToken) {
		MockRememberMeServices services = new MockRememberMeServices(uds);
		Cookie cookie = new Cookie(
				AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY,
				services.encodeCookie(StringUtils.delimitedListToStringArray(cookieToken,
						":")));

		return new Cookie[] { cookie };
	}

	private void assertCookieCancelled(MockHttpServletResponse response) {
		Cookie returnedCookie = response
				.getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
		assertNotNull(returnedCookie);
		assertEquals(0, returnedCookie.getMaxAge());
	}

	// ~ Inner Classes
	// ==================================================================================================

	static class MockRememberMeServices extends AbstractRememberMeServices {
		boolean loginSuccessCalled;

		MockRememberMeServices(String key, UserDetailsService userDetailsService) {
			super(key, userDetailsService);
		}

		MockRememberMeServices(UserDetailsService userDetailsService) {
			super("xxxx", userDetailsService);
		}

		MockRememberMeServices() {
			this(new MockUserDetailsService(null, false));
		}

		protected void onLoginSuccess(HttpServletRequest request,
				HttpServletResponse response, Authentication successfulAuthentication) {
			loginSuccessCalled = true;
		}

		protected UserDetails processAutoLoginCookie(String[] cookieTokens,
				HttpServletRequest request, HttpServletResponse response)
				throws RememberMeAuthenticationException {
			if (cookieTokens.length != 3) {
				throw new InvalidCookieException("deliberate exception");
			}

			UserDetails user = getUserDetailsService().loadUserByUsername("joe");

			return user;
		}
	}

	public static class MockUserDetailsService implements UserDetailsService {
		private UserDetails toReturn;
		private boolean throwException;

		public MockUserDetailsService() {
			this(null, false);
		}

		public MockUserDetailsService(UserDetails toReturn, boolean throwException) {
			this.toReturn = toReturn;
			this.throwException = throwException;
		}

		public UserDetails loadUserByUsername(String username) {
			if (throwException) {
				throw new UsernameNotFoundException("as requested by mock");
			}

			return toReturn;
		}

		public void setThrowException(boolean value) {
			this.throwException = value;
		}
	}
}
