package org.springframework.security.acls;

import org.springframework.security.acls.domain.AclFormattingUtils;
import org.springframework.security.acls.model.Permission;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Tests for {@link AclFormattingUtils}.
 *
 * @author Andrei Stefan
 */
public class AclFormattingUtilsTests extends TestCase {

	// ~ Methods
	// ========================================================================================================

	public final void testDemergePatternsParametersConstraints() throws Exception {
		try {
			AclFormattingUtils.demergePatterns(null, "SOME STRING");
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.demergePatterns("SOME STRING", null);
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.demergePatterns("SOME STRING", "LONGER SOME STRING");
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.demergePatterns("SOME STRING", "SAME LENGTH");
			Assert.assertTrue(true);
		}
		catch (IllegalArgumentException notExpected) {
			Assert.fail("It shouldn't have thrown IllegalArgumentException");
		}
	}

	public final void testDemergePatterns() throws Exception {
		String original = "...........................A...R";
		String removeBits = "...............................R";
		Assert.assertEquals("...........................A....",
				AclFormattingUtils.demergePatterns(original, removeBits));

		Assert.assertEquals("ABCDEF",
				AclFormattingUtils.demergePatterns("ABCDEF", "......"));
		Assert.assertEquals("......",
				AclFormattingUtils.demergePatterns("ABCDEF", "GHIJKL"));
	}

	public final void testMergePatternsParametersConstraints() throws Exception {
		try {
			AclFormattingUtils.mergePatterns(null, "SOME STRING");
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.mergePatterns("SOME STRING", null);
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.mergePatterns("SOME STRING", "LONGER SOME STRING");
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.mergePatterns("SOME STRING", "SAME LENGTH");
			Assert.assertTrue(true);
		}
		catch (IllegalArgumentException notExpected) {
			Assert.fail("It shouldn't have thrown IllegalArgumentException");
		}
	}

	public final void testMergePatterns() throws Exception {
		String original = "...............................R";
		String extraBits = "...........................A....";
		Assert.assertEquals("...........................A...R",
				AclFormattingUtils.mergePatterns(original, extraBits));

		Assert.assertEquals("ABCDEF",
				AclFormattingUtils.mergePatterns("ABCDEF", "......"));
		Assert.assertEquals("GHIJKL",
				AclFormattingUtils.mergePatterns("ABCDEF", "GHIJKL"));
	}

	public final void testBinaryPrints() throws Exception {
		Assert.assertEquals("............................****",
				AclFormattingUtils.printBinary(15));

		try {
			AclFormattingUtils.printBinary(15, Permission.RESERVED_ON);
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException notExpected) {
			Assert.assertTrue(true);
		}

		try {
			AclFormattingUtils.printBinary(15, Permission.RESERVED_OFF);
			Assert.fail("It should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException notExpected) {
			Assert.assertTrue(true);
		}

		Assert.assertEquals("............................xxxx",
				AclFormattingUtils.printBinary(15, 'x'));
	}

	public void testPrintBinaryNegative() {
		Assert.assertEquals("*...............................",
				AclFormattingUtils.printBinary(0x80000000));
	}

	public void testPrintBinaryMinusOne() {
		Assert.assertEquals("********************************",
				AclFormattingUtils.printBinary(0xffffffff));
	}
}
