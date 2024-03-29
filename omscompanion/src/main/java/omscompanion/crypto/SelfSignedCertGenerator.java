package omscompanion.crypto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Utility class for generating self-signed certificates. <a href=
 * "https://github.com/misterpki/selfsignedcert/blob/master/src/test/java/com/misterpki/SelfSignedCertGeneratorTest.java">GitHub
 * </a>
 *
 * @author Mister PKI
 */
public final class SelfSignedCertGenerator {

	private SelfSignedCertGenerator() {
	}

	/**
	 * Generates a self signed certificate using the BouncyCastle lib.
	 *
	 * @param keyPair       used for signing the certificate with PrivateKey
	 * @param hashAlgorithm Hash function
	 * @param cn            Common Name to be used in the subject dn
	 * @param days          validity period in days of the certificate
	 *
	 * @return self-signed X509Certificate
	 *
	 * @throws OperatorCreationException on creating a key id
	 * @throws CertIOException           on building JcaContentSignerBuilder
	 * @throws CertificateException      on getting certificate from provider
	 */
	public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String cn,
			final int days) throws OperatorCreationException, CertificateException, CertIOException {
		var now = Instant.now();
		var notBefore = Date.from(now);
		var notAfter = Date.from(now.plus(Duration.ofDays(days)));

		var contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());
		var x500Name = new X500Name("CN=" + cn);
		var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name, BigInteger.valueOf(now.toEpochMilli()),
				notBefore, notAfter, x500Name, keyPair.getPublic())
				.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()))
				.addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()))
				.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

		return new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider())
				.getCertificate(certificateBuilder.build(contentSigner));
	}

	/**
	 * Creates the hash value of the public key.
	 *
	 * @param publicKey of the certificate
	 *
	 * @return SubjectKeyIdentifier hash
	 *
	 * @throws OperatorCreationException
	 */
	private static SubjectKeyIdentifier createSubjectKeyId(final PublicKey publicKey) throws OperatorCreationException {
		var publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
		var digCalc = new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));

		return new X509ExtensionUtils(digCalc).createSubjectKeyIdentifier(publicKeyInfo);
	}

	/**
	 * Creates the hash value of the authority public key.
	 *
	 * @param publicKey of the authority certificate
	 *
	 * @return AuthorityKeyIdentifier hash
	 *
	 * @throws OperatorCreationException
	 */
	private static AuthorityKeyIdentifier createAuthorityKeyId(final PublicKey publicKey)
			throws OperatorCreationException {
		var publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
		var digCalc = new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));

		return new X509ExtensionUtils(digCalc).createAuthorityKeyIdentifier(publicKeyInfo);
	}
}