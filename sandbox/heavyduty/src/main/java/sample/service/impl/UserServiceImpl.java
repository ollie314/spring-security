/**
 *
 */
package sample.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sample.dao.UserDAO;
import sample.domain.User;
import sample.service.UserService;

/**
 * @author A207119
 *
 */
@Component
@Transactional
public class UserServiceImpl implements UserService {

	/** The logger */
	private static final Log LOG = LogFactory.getLog(UserServiceImpl.class);

	/** The User DAO */
	@Autowired
	private UserDAO userDAO = null;

	public UserDetails loadUserByUsername(String username)
			throws AuthenticationException {
		try {
			User user = userDAO.findByUsername(username);

			return new org.springframework.security.core.userdetails.User(user
					.getUsername(), user.getPassword(), true, true, true, true,
					AuthorityUtils.createAuthorityList("ROLE_USER"));
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			throw new UsernameNotFoundException("No matching account", e);
		}
	}

	public UserDetails register(String username, String password) {
		User user = new User(username, password);
		userDAO.persist(user);
		return new org.springframework.security.core.userdetails.User(user
				.getUsername(), user.getPassword(), true, true, true, true,
				AuthorityUtils.createAuthorityList("ROLE_USER"));

	}

	/**
	 * @param userDAO
	 *            the userDAO to set
	 */
	public void setUserDAO(UserDAO userDAO) {
		this.userDAO = userDAO;
	}

}
