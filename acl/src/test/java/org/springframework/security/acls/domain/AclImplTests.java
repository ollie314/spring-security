package org.springframework.security.acls.domain;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.*;
import org.springframework.security.acls.model.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.FieldUtils;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Tests for {@link AclImpl}.
 *
 * @author Andrei Stefan
 */
public class AclImplTests {
	private static final String TARGET_CLASS = "org.springframework.security.acls.TargetObject";
	private static final List<Permission> READ = Arrays.asList(BasePermission.READ);
	private static final List<Permission> WRITE = Arrays.asList(BasePermission.WRITE);
	private static final List<Permission> CREATE = Arrays.asList(BasePermission.CREATE);
	private static final List<Permission> DELETE = Arrays.asList(BasePermission.DELETE);
	private static final List<Sid> SCOTT = Arrays.asList((Sid) new PrincipalSid("scott"));
	private static final List<Sid> BEN = Arrays.asList((Sid) new PrincipalSid("ben"));

	Authentication auth = new TestingAuthenticationToken("joe", "ignored",
			"ROLE_ADMINISTRATOR");
	AclAuthorizationStrategy authzStrategy;
	PermissionGrantingStrategy pgs;
	AuditLogger mockAuditLogger;
	ObjectIdentity objectIdentity = new ObjectIdentityImpl(TARGET_CLASS, 100);

	// ~ Methods
	// ========================================================================================================

	@Before
	public void setUp() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(auth);
		authzStrategy = mock(AclAuthorizationStrategy.class);
		mockAuditLogger = mock(AuditLogger.class);
		pgs = new DefaultPermissionGrantingStrategy(mockAuditLogger);
		auth.setAuthenticated(true);
	}

	@After
	public void tearDown() throws Exception {
		SecurityContextHolder.clearContext();
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorsRejectNullObjectIdentity() throws Exception {
		try {
			new AclImpl(null, 1, authzStrategy, pgs, null, null, true, new PrincipalSid(
					"joe"));
			fail("Should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
		new AclImpl(null, 1, authzStrategy, mockAuditLogger);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorsRejectNullId() throws Exception {
		try {
			new AclImpl(objectIdentity, null, authzStrategy, pgs, null, null, true,
					new PrincipalSid("joe"));
			fail("Should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
		new AclImpl(objectIdentity, null, authzStrategy, mockAuditLogger);
	}

	@SuppressWarnings("deprecation")
	@Test(expected = IllegalArgumentException.class)
	public void constructorsRejectNullAclAuthzStrategy() throws Exception {
		try {
			new AclImpl(objectIdentity, 1, null, new DefaultPermissionGrantingStrategy(
					mockAuditLogger), null, null, true, new PrincipalSid("joe"));
			fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
		new AclImpl(objectIdentity, 1, null, mockAuditLogger);
	}

	@Test
	public void insertAceRejectsNullParameters() throws Exception {
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		try {
			acl.insertAce(0, null, new GrantedAuthoritySid("ROLE_IGNORED"), true);
			fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
		try {
			acl.insertAce(0, BasePermission.READ, null, true);
			fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
	}

	@Test
	public void insertAceAddsElementAtCorrectIndex() throws Exception {
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		MockAclService service = new MockAclService();

		// Insert one permission
		acl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST1"), true);
		service.updateAcl(acl);
		// Check it was successfully added
		assertEquals(1, acl.getEntries().size());
		assertEquals(acl.getEntries().get(0).getAcl(), acl);
		assertEquals(acl.getEntries().get(0).getPermission(), BasePermission.READ);
		assertEquals(acl.getEntries().get(0).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST1"));

		// Add a second permission
		acl.insertAce(1, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST2"), true);
		service.updateAcl(acl);
		// Check it was added on the last position
		assertEquals(2, acl.getEntries().size());
		assertEquals(acl.getEntries().get(1).getAcl(), acl);
		assertEquals(acl.getEntries().get(1).getPermission(), BasePermission.READ);
		assertEquals(acl.getEntries().get(1).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST2"));

		// Add a third permission, after the first one
		acl.insertAce(1, BasePermission.WRITE, new GrantedAuthoritySid("ROLE_TEST3"),
				false);
		service.updateAcl(acl);
		assertEquals(3, acl.getEntries().size());
		// Check the third entry was added between the two existent ones
		assertEquals(acl.getEntries().get(0).getPermission(), BasePermission.READ);
		assertEquals(acl.getEntries().get(0).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST1"));
		assertEquals(acl.getEntries().get(1).getPermission(), BasePermission.WRITE);
		assertEquals(acl.getEntries().get(1).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST3"));
		assertEquals(acl.getEntries().get(2).getPermission(), BasePermission.READ);
		assertEquals(acl.getEntries().get(2).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST2"));
	}

	@Test(expected = NotFoundException.class)
	public void insertAceFailsForNonExistentElement() throws Exception {
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		MockAclService service = new MockAclService();

		// Insert one permission
		acl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST1"), true);
		service.updateAcl(acl);

		acl.insertAce(55, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST2"),
				true);
	}

	@Test
	public void deleteAceKeepsInitialOrdering() throws Exception {
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		MockAclService service = new MockAclService();

		// Add several permissions
		acl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST1"), true);
		acl.insertAce(1, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST2"), true);
		acl.insertAce(2, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST3"), true);
		service.updateAcl(acl);

		// Delete first permission and check the order of the remaining permissions is
		// kept
		acl.deleteAce(0);
		assertEquals(2, acl.getEntries().size());
		assertEquals(acl.getEntries().get(0).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST2"));
		assertEquals(acl.getEntries().get(1).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST3"));

		// Add one more permission and remove the permission in the middle
		acl.insertAce(2, BasePermission.READ, new GrantedAuthoritySid("ROLE_TEST4"), true);
		service.updateAcl(acl);
		acl.deleteAce(1);
		assertEquals(2, acl.getEntries().size());
		assertEquals(acl.getEntries().get(0).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST2"));
		assertEquals(acl.getEntries().get(1).getSid(), new GrantedAuthoritySid(
				"ROLE_TEST4"));

		// Remove remaining permissions
		acl.deleteAce(1);
		acl.deleteAce(0);
		assertEquals(0, acl.getEntries().size());
	}

	@Test
	public void deleteAceFailsForNonExistentElement() throws Exception {
		AclAuthorizationStrategyImpl strategy = new AclAuthorizationStrategyImpl(
				new SimpleGrantedAuthority("ROLE_OWNERSHIP"), new SimpleGrantedAuthority(
						"ROLE_AUDITING"), new SimpleGrantedAuthority("ROLE_GENERAL"));
		MutableAcl acl = new AclImpl(objectIdentity, (1), strategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		try {
			acl.deleteAce(99);
			fail("It should have thrown NotFoundException");
		}
		catch (NotFoundException expected) {
		}
	}

	@Test
	public void isGrantingRejectsEmptyParameters() throws Exception {
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		Sid ben = new PrincipalSid("ben");
		try {
			acl.isGranted(new ArrayList<Permission>(0), Arrays.asList(ben), false);
			fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
		try {
			acl.isGranted(READ, new ArrayList<Sid>(0), false);
			fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
		}
	}

	@Test
	public void isGrantingGrantsAccessForAclWithNoParent() throws Exception {
		Authentication auth = new TestingAuthenticationToken("ben", "ignored",
				"ROLE_GENERAL", "ROLE_GUEST");
		auth.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(auth);
		ObjectIdentity rootOid = new ObjectIdentityImpl(TARGET_CLASS, 100);

		// Create an ACL which owner is not the authenticated principal
		MutableAcl rootAcl = new AclImpl(rootOid, 1, authzStrategy, pgs, null, null,
				false, new PrincipalSid("joe"));

		// Grant some permissions
		rootAcl.insertAce(0, BasePermission.READ, new PrincipalSid("ben"), false);
		rootAcl.insertAce(1, BasePermission.WRITE, new PrincipalSid("scott"), true);
		rootAcl.insertAce(2, BasePermission.WRITE, new PrincipalSid("rod"), false);
		rootAcl.insertAce(3, BasePermission.WRITE, new GrantedAuthoritySid(
				"WRITE_ACCESS_ROLE"), true);

		// Check permissions granting
		List<Permission> permissions = Arrays.asList(BasePermission.READ,
				BasePermission.CREATE);
		List<Sid> sids = Arrays.asList(new PrincipalSid("ben"), new GrantedAuthoritySid(
				"ROLE_GUEST"));
		assertFalse(rootAcl.isGranted(permissions, sids, false));
		try {
			rootAcl.isGranted(permissions, SCOTT, false);
			fail("It should have thrown NotFoundException");
		}
		catch (NotFoundException expected) {
		}
		assertTrue(rootAcl.isGranted(WRITE, SCOTT, false));
		assertFalse(rootAcl.isGranted(WRITE, Arrays.asList(new PrincipalSid("rod"),
				new GrantedAuthoritySid("WRITE_ACCESS_ROLE")), false));
		assertTrue(rootAcl.isGranted(WRITE, Arrays.asList(new GrantedAuthoritySid(
				"WRITE_ACCESS_ROLE"), new PrincipalSid("rod")), false));
		try {
			// Change the type of the Sid and check the granting process
			rootAcl.isGranted(WRITE, Arrays.asList(new GrantedAuthoritySid("rod"),
					new PrincipalSid("WRITE_ACCESS_ROLE")), false);
			fail("It should have thrown NotFoundException");
		}
		catch (NotFoundException expected) {
		}
	}

	@Test
	public void isGrantingGrantsAccessForInheritableAcls() throws Exception {
		Authentication auth = new TestingAuthenticationToken("ben", "ignored",
				"ROLE_GENERAL");
		auth.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(auth);
		ObjectIdentity grandParentOid = new ObjectIdentityImpl(TARGET_CLASS, 100);
		ObjectIdentity parentOid1 = new ObjectIdentityImpl(TARGET_CLASS, 101);
		ObjectIdentity parentOid2 = new ObjectIdentityImpl(TARGET_CLASS, 102);
		ObjectIdentity childOid1 = new ObjectIdentityImpl(TARGET_CLASS, 103);
		ObjectIdentity childOid2 = new ObjectIdentityImpl(TARGET_CLASS, 104);

		// Create ACLs
		PrincipalSid joe = new PrincipalSid("joe");
		MutableAcl grandParentAcl = new AclImpl(grandParentOid, 1, authzStrategy, pgs,
				null, null, false, joe);
		MutableAcl parentAcl1 = new AclImpl(parentOid1, 2, authzStrategy, pgs, null,
				null, true, joe);
		MutableAcl parentAcl2 = new AclImpl(parentOid2, 3, authzStrategy, pgs, null,
				null, true, joe);
		MutableAcl childAcl1 = new AclImpl(childOid1, 4, authzStrategy, pgs, null, null,
				true, joe);
		MutableAcl childAcl2 = new AclImpl(childOid2, 4, authzStrategy, pgs, null, null,
				false, joe);

		// Create hierarchies
		childAcl2.setParent(childAcl1);
		childAcl1.setParent(parentAcl1);
		parentAcl2.setParent(grandParentAcl);
		parentAcl1.setParent(grandParentAcl);

		// Add some permissions
		grandParentAcl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid(
				"ROLE_USER_READ"), true);
		grandParentAcl.insertAce(1, BasePermission.WRITE, new PrincipalSid("ben"), true);
		grandParentAcl
				.insertAce(2, BasePermission.DELETE, new PrincipalSid("ben"), false);
		grandParentAcl.insertAce(3, BasePermission.DELETE, new PrincipalSid("scott"),
				true);
		parentAcl1.insertAce(0, BasePermission.READ, new PrincipalSid("scott"), true);
		parentAcl1.insertAce(1, BasePermission.DELETE, new PrincipalSid("scott"), false);
		parentAcl2.insertAce(0, BasePermission.CREATE, new PrincipalSid("ben"), true);
		childAcl1.insertAce(0, BasePermission.CREATE, new PrincipalSid("scott"), true);

		// Check granting process for parent1
		assertTrue(parentAcl1.isGranted(READ, SCOTT, false));
		assertTrue(parentAcl1.isGranted(READ,
				Arrays.asList((Sid) new GrantedAuthoritySid("ROLE_USER_READ")), false));
		assertTrue(parentAcl1.isGranted(WRITE, BEN, false));
		assertFalse(parentAcl1.isGranted(DELETE, BEN, false));
		assertFalse(parentAcl1.isGranted(DELETE, SCOTT, false));

		// Check granting process for parent2
		assertTrue(parentAcl2.isGranted(CREATE, BEN, false));
		assertTrue(parentAcl2.isGranted(WRITE, BEN, false));
		assertFalse(parentAcl2.isGranted(DELETE, BEN, false));

		// Check granting process for child1
		assertTrue(childAcl1.isGranted(CREATE, SCOTT, false));
		assertTrue(childAcl1.isGranted(READ,
				Arrays.asList((Sid) new GrantedAuthoritySid("ROLE_USER_READ")), false));
		assertFalse(childAcl1.isGranted(DELETE, BEN, false));

		// Check granting process for child2 (doesn't inherit the permissions from its
		// parent)
		try {
			assertTrue(childAcl2.isGranted(CREATE, SCOTT, false));
			fail("It should have thrown NotFoundException");
		}
		catch (NotFoundException expected) {
			assertTrue(true);
		}
		try {
			assertTrue(childAcl2.isGranted(CREATE,
					Arrays.asList((Sid) new PrincipalSid("joe")), false));
			fail("It should have thrown NotFoundException");
		}
		catch (NotFoundException expected) {
			assertTrue(true);
		}
	}

	@Test
	public void updatedAceValuesAreCorrectlyReflectedInAcl() throws Exception {
		Authentication auth = new TestingAuthenticationToken("ben", "ignored",
				"ROLE_GENERAL");
		auth.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(auth);
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				false, new PrincipalSid("joe"));
		MockAclService service = new MockAclService();

		acl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid("ROLE_USER_READ"),
				true);
		acl.insertAce(1, BasePermission.WRITE, new GrantedAuthoritySid("ROLE_USER_READ"),
				true);
		acl.insertAce(2, BasePermission.CREATE, new PrincipalSid("ben"), true);
		service.updateAcl(acl);

		assertEquals(acl.getEntries().get(0).getPermission(), BasePermission.READ);
		assertEquals(acl.getEntries().get(1).getPermission(), BasePermission.WRITE);
		assertEquals(acl.getEntries().get(2).getPermission(), BasePermission.CREATE);

		// Change each permission
		acl.updateAce(0, BasePermission.CREATE);
		acl.updateAce(1, BasePermission.DELETE);
		acl.updateAce(2, BasePermission.READ);

		// Check the change was successfully made
		assertEquals(acl.getEntries().get(0).getPermission(), BasePermission.CREATE);
		assertEquals(acl.getEntries().get(1).getPermission(), BasePermission.DELETE);
		assertEquals(acl.getEntries().get(2).getPermission(), BasePermission.READ);
	}

	@Test
	public void auditableEntryFlagsAreUpdatedCorrectly() throws Exception {
		Authentication auth = new TestingAuthenticationToken("ben", "ignored",
				"ROLE_AUDITING", "ROLE_GENERAL");
		auth.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(auth);
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				false, new PrincipalSid("joe"));
		MockAclService service = new MockAclService();

		acl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid("ROLE_USER_READ"),
				true);
		acl.insertAce(1, BasePermission.WRITE, new GrantedAuthoritySid("ROLE_USER_READ"),
				true);
		service.updateAcl(acl);

		assertFalse(((AuditableAccessControlEntry) acl.getEntries().get(0))
				.isAuditFailure());
		assertFalse(((AuditableAccessControlEntry) acl.getEntries().get(1))
				.isAuditFailure());
		assertFalse(((AuditableAccessControlEntry) acl.getEntries().get(0))
				.isAuditSuccess());
		assertFalse(((AuditableAccessControlEntry) acl.getEntries().get(1))
				.isAuditSuccess());

		// Change each permission
		((AuditableAcl) acl).updateAuditing(0, true, true);
		((AuditableAcl) acl).updateAuditing(1, true, true);

		// Check the change was successfuly made
		assertTrue(((AuditableAccessControlEntry) acl.getEntries().get(0))
				.isAuditFailure());
		assertTrue(((AuditableAccessControlEntry) acl.getEntries().get(1))
				.isAuditFailure());
		assertTrue(((AuditableAccessControlEntry) acl.getEntries().get(0))
				.isAuditSuccess());
		assertTrue(((AuditableAccessControlEntry) acl.getEntries().get(1))
				.isAuditSuccess());
	}

	@Test
	public void gettersAndSettersAreConsistent() throws Exception {
		Authentication auth = new TestingAuthenticationToken("ben", "ignored",
				"ROLE_GENERAL");
		auth.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(auth);
		ObjectIdentity identity = new ObjectIdentityImpl(TARGET_CLASS, (100));
		ObjectIdentity identity2 = new ObjectIdentityImpl(TARGET_CLASS, (101));
		MutableAcl acl = new AclImpl(identity, 1, authzStrategy, pgs, null, null, true,
				new PrincipalSid("joe"));
		MutableAcl parentAcl = new AclImpl(identity2, 2, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		MockAclService service = new MockAclService();
		acl.insertAce(0, BasePermission.READ, new GrantedAuthoritySid("ROLE_USER_READ"),
				true);
		acl.insertAce(1, BasePermission.WRITE, new GrantedAuthoritySid("ROLE_USER_READ"),
				true);
		service.updateAcl(acl);

		assertEquals(acl.getId(), 1);
		assertEquals(acl.getObjectIdentity(), identity);
		assertEquals(acl.getOwner(), new PrincipalSid("joe"));
		assertNull(acl.getParentAcl());
		assertTrue(acl.isEntriesInheriting());
		assertEquals(2, acl.getEntries().size());

		acl.setParent(parentAcl);
		assertEquals(acl.getParentAcl(), parentAcl);

		acl.setEntriesInheriting(false);
		assertFalse(acl.isEntriesInheriting());

		acl.setOwner(new PrincipalSid("ben"));
		assertEquals(acl.getOwner(), new PrincipalSid("ben"));
	}

	@Test
	public void isSidLoadedBehavesAsExpected() throws Exception {
		List<Sid> loadedSids = Arrays.asList(new PrincipalSid("ben"),
				new GrantedAuthoritySid("ROLE_IGNORED"));
		MutableAcl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null,
				loadedSids, true, new PrincipalSid("joe"));

		assertTrue(acl.isSidLoaded(loadedSids));
		assertTrue(acl.isSidLoaded(Arrays.asList(new GrantedAuthoritySid("ROLE_IGNORED"),
				new PrincipalSid("ben"))));
		assertTrue(acl.isSidLoaded(Arrays.asList((Sid) new GrantedAuthoritySid(
				"ROLE_IGNORED"))));
		assertTrue(acl.isSidLoaded(BEN));
		assertTrue(acl.isSidLoaded(null));
		assertTrue(acl.isSidLoaded(new ArrayList<Sid>(0)));
		assertTrue(acl.isSidLoaded(Arrays.asList((Sid) new GrantedAuthoritySid(
				"ROLE_IGNORED"), new GrantedAuthoritySid("ROLE_IGNORED"))));
		assertFalse(acl.isSidLoaded(Arrays.asList((Sid) new GrantedAuthoritySid(
				"ROLE_GENERAL"), new GrantedAuthoritySid("ROLE_IGNORED"))));
		assertFalse(acl.isSidLoaded(Arrays.asList((Sid) new GrantedAuthoritySid(
				"ROLE_IGNORED"), new GrantedAuthoritySid("ROLE_GENERAL"))));
	}

	@Test(expected = NotFoundException.class)
	public void insertAceRaisesNotFoundExceptionForIndexLessThanZero() throws Exception {
		AclImpl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		acl.insertAce(-1, mock(Permission.class), mock(Sid.class), true);
	}

	@Test(expected = NotFoundException.class)
	public void deleteAceRaisesNotFoundExceptionForIndexLessThanZero() throws Exception {
		AclImpl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		acl.deleteAce(-1);
	}

	@Test(expected = NotFoundException.class)
	public void insertAceRaisesNotFoundExceptionForIndexGreaterThanSize()
			throws Exception {
		AclImpl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		// Insert at zero, OK.
		acl.insertAce(0, mock(Permission.class), mock(Sid.class), true);
		// Size is now 1
		acl.insertAce(2, mock(Permission.class), mock(Sid.class), true);
	}

	// SEC-1151
	@Test(expected = NotFoundException.class)
	public void deleteAceRaisesNotFoundExceptionForIndexEqualToSize() throws Exception {
		AclImpl acl = new AclImpl(objectIdentity, 1, authzStrategy, pgs, null, null,
				true, new PrincipalSid("joe"));
		acl.insertAce(0, mock(Permission.class), mock(Sid.class), true);
		// Size is now 1
		acl.deleteAce(1);
	}

	// SEC-1795
	@Test
	public void changingParentIsSuccessful() throws Exception {
		AclImpl parentAcl = new AclImpl(objectIdentity, 1L, authzStrategy,
				mockAuditLogger);
		AclImpl childAcl = new AclImpl(objectIdentity, 2L, authzStrategy, mockAuditLogger);
		AclImpl changeParentAcl = new AclImpl(objectIdentity, 3L, authzStrategy,
				mockAuditLogger);

		childAcl.setParent(parentAcl);
		childAcl.setParent(changeParentAcl);
	}

	// ~ Inner Classes
	// ==================================================================================================

	private class MockAclService implements MutableAclService {
		public MutableAcl createAcl(ObjectIdentity objectIdentity)
				throws AlreadyExistsException {
			return null;
		}

		public void deleteAcl(ObjectIdentity objectIdentity, boolean deleteChildren)
				throws ChildrenExistException {
		}

		/*
		 * Mock implementation that populates the aces list with fully initialized
		 * AccessControlEntries
		 * 
		 * @see
		 * org.springframework.security.acls.MutableAclService#updateAcl(org.springframework
		 * .security.acls.MutableAcl)
		 */
		@SuppressWarnings("unchecked")
		public MutableAcl updateAcl(MutableAcl acl) throws NotFoundException {
			List<AccessControlEntry> oldAces = acl.getEntries();
			Field acesField = FieldUtils.getField(AclImpl.class, "aces");
			acesField.setAccessible(true);
			List newAces;
			try {
				newAces = (List) acesField.get(acl);
				newAces.clear();

				for (int i = 0; i < oldAces.size(); i++) {
					AccessControlEntry ac = oldAces.get(i);
					// Just give an ID to all this acl's aces, rest of the fields are just
					// copied
					newAces.add(new AccessControlEntryImpl((i + 1), ac.getAcl(), ac
							.getSid(), ac.getPermission(), ac.isGranting(),
							((AuditableAccessControlEntry) ac).isAuditSuccess(),
							((AuditableAccessControlEntry) ac).isAuditFailure()));
				}
			}
			catch (IllegalAccessException e) {
				e.printStackTrace();
			}

			return acl;
		}

		public List<ObjectIdentity> findChildren(ObjectIdentity parentIdentity) {
			return null;
		}

		public Acl readAclById(ObjectIdentity object) throws NotFoundException {
			return null;
		}

		public Acl readAclById(ObjectIdentity object, List<Sid> sids)
				throws NotFoundException {
			return null;
		}

		public Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects)
				throws NotFoundException {
			return null;
		}

		public Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects,
				List<Sid> sids) throws NotFoundException {
			return null;
		}
	}
}
