package com.seomoz.api.authentication;

import java.io.InputStream;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;

import ai.vacuity.rudi.adaptors.bo.Config;
import ai.vacuity.rudi.adaptors.interfaces.IEvent;
import ai.vacuity.rudi.adaptors.interfaces.impl.AbstractTemplateProcessor;

/**
 * The authentication class which is used to generate the authentication string
 * 
 * @author Radeep Solutions
 */
public class Authenticator extends AbstractTemplateProcessor {
	private final static org.slf4j.Logger logger = LoggerFactory.getLogger(Authenticator.class);
	private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

	/**
	 * accessID The user's Access ID
	 */
	private String accessID;

	/**
	 * secretKey The user's Secret Key
	 */
	private String secretKey;

	/**
	 * expiresInterval The interval after which the authentication string expires Default 300s
	 */
	private long expiresInterval = 300;

	public Authenticator() {
		Properties p = new Properties();
		String resourceName = "api.config"; // could also be a constant
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try (InputStream resourceStream = loader.getResourceAsStream(resourceName)) {
			p.load(resourceStream);
			this.accessID = p.getProperty("moz.id");
			this.secretKey = p.getProperty("moz.key");
			if (Config.getMap() != null && Config.getMap().containsKey("moz")) {
				this.accessID = (Config.getMap().get("moz").hasId()) ? Config.getMap().get("moz").getId() : p.getProperty("moz.id");
				this.secretKey = (Config.getMap().get("moz").hasKey()) ? Config.getMap().get("moz").getKey() : p.getProperty("moz.key");
			}
		}
		catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
		}

	}

	public static void main(String[] args) {
		Properties p = new Properties();
		String resourceName = "api.config"; // could also be a constant
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try (InputStream resourceStream = loader.getResourceAsStream(resourceName)) {
			p.load(resourceStream);
			Authenticator a = new Authenticator();
			String[] oa = a.getAuthenticationMaterial();
			logger.debug("Auth String: " + "AccessID=" + a.accessID + "&Expires=" + oa[0] + "&Signature=" + oa[2]);
		}
		catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
		}
	}

	/**
	 * Constructor to set all the variables
	 * 
	 * @param accessID
	 * @param secretKey
	 * @param expiresInterval
	 */
	public Authenticator(String accessID, String secretKey, long expiresInterval) {
		this.accessID = accessID;
		this.secretKey = secretKey;
		this.expiresInterval = expiresInterval;
	}

	/**
	 * 
	 * This method calculates the authentication String based on the user's credentials.
	 * 
	 * Set the user credentials before calling this method
	 * 
	 * @return the authentication string
	 * 
	 * @see #setAccessID(String)
	 * @see #setSecretKey(String)
	 */
	public String[] getAuthenticationMaterial() {
		long expires = ((new Date()).getTime()) / 1000 + expiresInterval;

		String stringToSign = accessID + "\n" + expires;

		SecretKeySpec signingKey = new SecretKeySpec(secretKey.getBytes(), HMAC_SHA1_ALGORITHM);

		// get an hmac_sha1 Mac instance and initialize with the signing key
		Mac mac = null;
		try {
			mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			mac.init(signingKey);
		}
		catch (NoSuchAlgorithmException e) {
			logger.error(e.getMessage(), e);
			return new String[] {};
		}
		catch (InvalidKeyException e) {
			logger.error(e.getMessage(), e);
			return new String[] {};
		}

		// compute the hmac on input data bytes
		byte[] rawHmac = mac.doFinal(stringToSign.getBytes());

		// base64-encode the hmac
		String urlSafeSignature = URLEncoder.encode(EncodeBase64(rawHmac));

		// String authenticationStr = "AccessID=" + accessID + "&Expires=" + expires + "&Signature=" + urlSafeSignature;
		//
		// return authenticationStr;

		return new String[] { expires + "", EncodeBase64(rawHmac), urlSafeSignature };
	}

	/**
	 * Encodes the rawdata in Base64 format
	 * 
	 * @param rawData
	 * @return
	 */
	public String EncodeBase64(byte[] rawData) {
		return Base64.encodeBytes(rawData);
	}

	/**
	 * @return the accessID
	 */
	public String getAccessID() {
		return accessID;
	}

	/**
	 * @param accessID
	 *            the accessID to set
	 */
	public void setAccessID(String accessID) {
		this.accessID = accessID;
	}

	/**
	 * @return the secretKey
	 */
	public String getSecretKey() {
		return secretKey;
	}

	/**
	 * @param secretKey
	 *            the secretKey to set
	 */
	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	/**
	 * @return the expiresInterval
	 */
	public long getExpiresInterval() {
		return expiresInterval;
	}

	/**
	 * @param expiresInterval
	 *            the expiresInterval to set
	 */
	public void setExpiresInterval(long expiresInterval) {
		this.expiresInterval = expiresInterval;
	}

	@Override
	public void process(String template, IEvent event) {
		super.process(template, event);
		String[] oa = getAuthenticationMaterial();
		this.template = this.template.replace("${id}", getAccessID());
		this.template = this.template.replace("${expiry}", oa[0]);
		this.template = this.template.replace("${token}", oa[2]);
		logger.debug("Processing Template: " + this.template);
	}

	@Override
	public String getTemplate() {
		return this.template;
	}

	@Override
	public IEvent getEvent() {
		return this.event;
	}
}