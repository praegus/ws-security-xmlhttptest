package nl.praegus.fitnesse.slim.fixtures.helper;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPMessage;
import nl.hsac.fitnesse.fixture.Environment;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecTimestamp;
import org.apache.wss4j.dom.message.WSSecUsernameToken;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Properties;

public class SoapSigningHelper {

    // Value constants are defined as Strings instead of array(list)s, so that they can be referenced from JavaDoc.
    public static final String VALID_DIGEST_ALGORITHMS = "SHA1, SHA256, SHA512, RIPEMD160";
    public static final String VALID_KEY_IDENTIFIER_TYPES = "BST_DIRECT_REFERENCE, ISSUER_SERIAL, X509_KEY_IDENTIFIER, " +
            "SKI_KEY_IDENTIFIER, EMBEDDED_KEYNAME, EMBED_SECURITY_TOKEN_REF";
    public static final String VALID_KEY_STORE_TYPES = "JKS, JCEKS, PKCS11";
    public static final String VALID_CANONICALIZATION_METHODS = "INCLUSIVE, INCLUSIVE_WITH_COMMENTS, EXCLUSIVE, EXCLUSIVE_WITH_COMMENTS";
    public static final String COMMA_SEPARATED_REGEX = "\\s*,\\s*";

    private final Properties signatureKeyProperties = new Properties();

    private int signatureKeyIdentifierType;
    private String signatureCanonicalizationMethod;
    private String signatureDigestAlgorithm;
    private boolean signatureUseSingleCertificate = false;

    private int timestampTTL = 0;
    private boolean timestampPrecisionInMillis = false;

    private String tokenUsername;
    private String tokenPassword;
    private boolean tokenAddNonce = false;
    private boolean tokenAddCreated = false;

    private boolean signature = false;
    private boolean timestamp = false;
    private boolean usernameToken = false;

    public void applySignature(boolean signature) {
        this.signature = signature;
    }

    public void applyTimestamp(boolean timestamp) {
        this.timestamp = timestamp;
    }

    public void applyUsernameToken(boolean usernameToken) {
        this.usernameToken = usernameToken;
    }

    public void setTokenUsername(String user) {
        tokenUsername = user;
    }

    public void setTokenPassword(String password) {
        tokenPassword = password;
    }

    public void addNonceToToken(boolean addNonce) {
        tokenAddNonce = addNonce;
    }

    public void addCreatedToToken(boolean addCreated) {
        tokenAddCreated = addCreated;
    }

    public void setTimestampTTL(int ttl) {
        timestampTTL = ttl;
    }

    public void setTimestampPrecisionToMillis(boolean precisionInMillis) {
        timestampPrecisionInMillis = precisionInMillis;
    }

    /**
     * Set keystore to use
     *
     * @param keyStore (wiki)file path to keystore file
     * @param ksType   Type of keystore. Can be JKS, JCEKS or PKCS11
     */
    public void setKeyStore(String keyStore, String ksType) {
        if (Arrays.stream(VALID_KEY_STORE_TYPES.split(COMMA_SEPARATED_REGEX)).noneMatch(ksType.toUpperCase()::equals)) {
            throw new SlimFixtureException(false, "Invalid keystore type: " + ksType.toUpperCase() + ". Valid options: " + VALID_KEY_STORE_TYPES);
        }
        signatureKeyProperties.put("org.apache.ws.security.crypto.provider", "org.apache.ws.security.components.crypto.Merlin");
        signatureKeyProperties.put("org.apache.ws.security.crypto.merlin.keystore.file", Environment.getInstance().getFilePathFromWikiUrl(keyStore));
        signatureKeyProperties.put("org.apache.ws.security.crypto.merlin.keystore.type", ksType.toUpperCase());
    }

    public void setKeyStorePassword(String keyStorePassword) {
        signatureKeyProperties.put("org.apache.ws.security.crypto.merlin.keystore.password", keyStorePassword);
    }

    public void setKeyAlias(String keyAlias) {
        signatureKeyProperties.put("org.apache.ws.security.crypto.merlin.keystore.alias", keyAlias);
    }

    public void setKeyPassword(String keyPassword) {
        signatureKeyProperties.put("privatekeypassword", keyPassword);
    }

    /**
     * Set the identifier type.
     *
     * @param type Type name from WSS4J's WSCONSTANTS.
     */
    public void setKeyIdentifierType(String type) {
        if (Arrays.stream(VALID_KEY_IDENTIFIER_TYPES.split(COMMA_SEPARATED_REGEX)).noneMatch(type.toUpperCase()::equals)) {
            throw new SlimFixtureException(false, "Invalid key identifier type: " + type.toUpperCase() + ". Valid options: " +VALID_KEY_IDENTIFIER_TYPES);
        }
        signatureKeyIdentifierType = getIntValueFromField(type.toUpperCase());
    }

    public void useSingleCertificate(boolean useSingleCert) {
        signatureUseSingleCertificate = useSingleCert;
    }

    /**
     * Set the canonicalization
     *
     * @param cMethod Canonicalization Method to use.
     */
    public void setCanonicalizationMethod(String cMethod) {
        if (Arrays.stream(VALID_CANONICALIZATION_METHODS.split(COMMA_SEPARATED_REGEX)).noneMatch(cMethod.toUpperCase()::equals)) {
            throw new SlimFixtureException(false, "Invalid canonicalization method: " + cMethod.toUpperCase() + ". Valid options: " + VALID_CANONICALIZATION_METHODS);
        }
        signatureCanonicalizationMethod = getStringValueFromField(CanonicalizationMethod.class, cMethod.toUpperCase());
    }

    /**
     * Set the digest algorithm to use
     *
     * @param digestMethod The method to use.
     */
    public void setDigestAlgorithm(String digestMethod) {
        if (Arrays.stream(VALID_DIGEST_ALGORITHMS.split(COMMA_SEPARATED_REGEX)).noneMatch(digestMethod.toUpperCase()::equals)) {
            throw new SlimFixtureException(false, "Invalid digest method: " + digestMethod.toUpperCase() + ". Valid options: " + VALID_DIGEST_ALGORITHMS);
        }
        signatureDigestAlgorithm = getStringValueFromField(DigestMethod.class, digestMethod.toUpperCase());
    }

    public String signSoapMessageIfNeeded(String message) {
        if(timestamp || usernameToken || signature) {
            try {
                SOAPMessage soapMessage = MessageFactory.newInstance().createMessage(null, new ByteArrayInputStream(message.getBytes()));
                Document doc = soapMessage.getSOAPPart().getEnvelope().getBody().getOwnerDocument();

                if (timestamp) {
                    doc = addTimestamp(doc);
                }
                if (usernameToken) {
                    doc = addUsernameToken(doc);
                }
                if (signature) {
                    doc = addSignature(doc);
                }

                return org.apache.wss4j.common.util.XMLUtils.prettyDocumentToString(doc);

            } catch (Exception e) {
                throw new SlimFixtureException(true, "Error Signing XML", e.getCause());
            }
        } else {
            return message;
        }

    }

    private Document addSignature(Document doc) {
        try {
            Crypto crypto = CryptoFactory.getInstance(signatureKeyProperties);

            WSSecSignature wsSecSignature = new WSSecSignature(secHeader(doc));
            wsSecSignature.setUserInfo(signatureKeyProperties.getProperty("org.apache.ws.security.crypto.merlin.keystore.alias"),
                    signatureKeyProperties.getProperty("privatekeypassword"));
            wsSecSignature.setKeyIdentifierType(signatureKeyIdentifierType);
            wsSecSignature.setUseSingleCertificate(signatureUseSingleCertificate);
            wsSecSignature.setSigCanonicalization(signatureCanonicalizationMethod);
            wsSecSignature.setDigestAlgo(signatureDigestAlgorithm);

            return wsSecSignature.build(crypto);

        } catch (Exception e) {
            throw new SlimFixtureException(true, "ERR", e);
        }
    }

    private Document addTimestamp(Document doc) {
        try {
            WSSecTimestamp wsSecTimestamp = new WSSecTimestamp(secHeader(doc));
            wsSecTimestamp.setTimeToLive(timestampTTL);
            wsSecTimestamp.setPrecisionInMilliSeconds(timestampPrecisionInMillis);

            return wsSecTimestamp.build();
        } catch (Exception e) {
            throw new SlimFixtureException(true, "ERR", e);
        }
    }

    private Document addUsernameToken(Document doc) {
        try {
            WSSecUsernameToken tokenBuilder = new WSSecUsernameToken(secHeader(doc));
            tokenBuilder.setUserInfo(tokenUsername, tokenPassword);
            if (tokenAddNonce) {
                tokenBuilder.addNonce();
            }
            if (tokenAddCreated) {
                tokenBuilder.addCreated();
            }

            return tokenBuilder.build();
        } catch (Exception e) {
            throw new SlimFixtureException(true, "ERR", e);
        }
    }

    private int getIntValueFromField(String field) {
        try {
            return WSConstants.class.getDeclaredField(field).getInt(null);
        } catch (Exception e) {
            throw new SlimFixtureException(field + " is not an accessible property of " + WSConstants.class.getCanonicalName());
        }
    }

    private String getStringValueFromField(Class<?> cls, String field) {
        try {
            return cls.getDeclaredField(field).get(null).toString();
        } catch (Exception e) {
            throw new SlimFixtureException(field + " is not an accessible property of " + cls.getCanonicalName());
        }
    }

    private WSSecHeader secHeader(Document doc) throws WSSecurityException {
        WSSecHeader sh = new WSSecHeader(doc);
        sh.insertSecurityHeader();
        return sh;
    }
}
