package omscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Test;

public class SelfSignedCertGeneratorTest {

	@Test
	public void createSelfSignedCertificate()
			throws CertificateException, CertIOException, OperatorCreationException, NoSuchAlgorithmException {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(4096);
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		final X509Certificate cert = SelfSignedCertGenerator.generate(keyPair, "SHA256withRSA", "localhost", 730);
		assertNull(cert.getKeyUsage());
		assertNull(cert.getExtendedKeyUsage());
		assertEquals("X.509", cert.getType());
		assertEquals("CN=localhost", cert.getSubjectX500Principal().getName());
		assertEquals(cert.getSubjectX500Principal(), cert.getIssuerX500Principal());
		assertEquals("SHA256withRSA", cert.getSigAlgName());
		assertEquals(3, cert.getVersion());
	}
}