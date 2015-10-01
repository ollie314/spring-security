package sample.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {

	/**
	 * Register a new User in database
	 * @param username
	 */
	public UserDetails register(String username, String password);



}
