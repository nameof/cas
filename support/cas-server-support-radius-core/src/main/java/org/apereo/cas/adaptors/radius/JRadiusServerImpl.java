package org.apereo.cas.adaptors.radius;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.jradius.client.RadiusClient;
import net.jradius.client.auth.RadiusAuthenticator;
import net.jradius.dictionary.Attr_ClientIPAddress;
import net.jradius.dictionary.Attr_NASIPAddress;
import net.jradius.dictionary.Attr_NASIPv6Address;
import net.jradius.dictionary.Attr_NASIdentifier;
import net.jradius.dictionary.Attr_NASPort;
import net.jradius.dictionary.Attr_NASPortId;
import net.jradius.dictionary.Attr_NASPortType;
import net.jradius.dictionary.Attr_State;
import net.jradius.dictionary.Attr_UserName;
import net.jradius.dictionary.Attr_UserPassword;
import net.jradius.dictionary.vsa_redback.Attr_NASRealPort;
import net.jradius.packet.AccessReject;
import net.jradius.packet.AccessRequest;
import net.jradius.packet.attribute.AttributeFactory;
import net.jradius.packet.attribute.AttributeList;
import net.jradius.packet.attribute.RadiusAttribute;
import org.apache.commons.lang3.StringUtils;
import org.apereo.inspektr.common.web.ClientInfo;
import org.apereo.inspektr.common.web.ClientInfoHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.Serializable;
import java.security.Security;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of a RadiusServer that utilizes the JRadius packages available
 * at <a href="http://jradius.sf.net">http://jradius.sf.net</a>.
 *
 * @author Scott Battaglia
 * @author Marvin S. Addison
 * @author Misagh Moayyed
 * @since 3.1
 */
@Slf4j
@ToString
@Setter
@Getter
public class JRadiusServerImpl implements RadiusServer {

    /**
     * Default retry count, {@value}.
     **/
    public static final int DEFAULT_RETRY_COUNT = 3;

    private static final long serialVersionUID = -7122734096722096617L;

    /**
     * RADIUS protocol.
     */
    private final RadiusProtocol protocol;

    /**
     * Produces RADIUS client instances for authentication.
     */
    private final RadiusClientFactory radiusClientFactory;

    /**
     * Number of times to retry authentication when no response is received.
     */
    private final int retries;

    private final String nasIpAddress;

    private final String nasIpv6Address;

    private final long nasPort;

    private final long nasPortId;

    private final String nasIdentifier;

    private final long nasRealPort;

    private final long nasPortType;

    static {
        AttributeFactory.loadAttributeDictionary("net.jradius.dictionary.AttributeDictionaryImpl");
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Instantiates a new J radius server.
     *
     * @param protocol            the protocol
     * @param radiusClientFactory the radius client factory
     */
    public JRadiusServerImpl(final RadiusProtocol protocol, final RadiusClientFactory radiusClientFactory) {
        this(protocol, radiusClientFactory, 1, null, null, -1, -1, null, -1, -1);
    }

    /**
     * Instantiates a new server implementation
     * with the radius protocol and client factory specified.
     *
     * @param protocol       the protocol
     * @param clientFactory  the client factory
     * @param retries        the new retries
     * @param nasIpAddress   the new nas ip address
     * @param nasIpv6Address the new nas ipv6 address
     * @param nasPort        the new nas port
     * @param nasPortId      the new nas port id
     * @param nasIdentifier  the new nas identifier
     * @param nasRealPort    the new nas real port
     * @param nasPortType    the nas port type
     */
    public JRadiusServerImpl(final RadiusProtocol protocol, final RadiusClientFactory clientFactory, final int retries,
                             final String nasIpAddress, final String nasIpv6Address, final long nasPort, final long nasPortId,
                             final String nasIdentifier, final long nasRealPort, final long nasPortType) {
        this.protocol = protocol;
        this.radiusClientFactory = clientFactory;
        this.retries = retries;
        this.nasIpAddress = nasIpAddress;
        this.nasIpv6Address = nasIpv6Address;
        this.nasPort = nasPort;
        this.nasPortId = nasPortId;
        this.nasIdentifier = nasIdentifier;
        this.nasRealPort = nasRealPort;
        this.nasPortType = nasPortType;
    }

    @Override
    public RadiusResponse authenticate(final String username, final String password, final Optional state) throws Exception {
        final AttributeList attributeList = new AttributeList();
        attributeList.add(new Attr_UserName(username));

        if (StringUtils.isNotBlank(password)) {
            attributeList.add(new Attr_UserPassword(password));
        }

        final ClientInfo clientInfo = ClientInfoHolder.getClientInfo();
        if (clientInfo != null) {
            final String clientIpAddress = clientInfo.getClientIpAddress();
            final Attr_ClientIPAddress clientIpAttribute = new Attr_ClientIPAddress(clientIpAddress);
            LOGGER.debug("Adding client IP address attribute [{}]", clientIpAttribute);
            attributeList.add(clientIpAttribute);
        }

        state.ifPresent(value -> attributeList.add(new Attr_State((Serializable) value)));

        if (StringUtils.isNotBlank(this.nasIpAddress)) {
            attributeList.add(new Attr_NASIPAddress(this.nasIpAddress));
        }
        if (StringUtils.isNotBlank(this.nasIpv6Address)) {
            attributeList.add(new Attr_NASIPv6Address(this.nasIpv6Address));
        }
        if (this.nasPort != -1) {
            attributeList.add(new Attr_NASPort(this.nasPort));
        }
        if (this.nasPortId != -1) {
            attributeList.add(new Attr_NASPortId(this.nasPortId));
        }
        if (StringUtils.isNotBlank(this.nasIdentifier)) {
            attributeList.add(new Attr_NASIdentifier(this.nasIdentifier));
        }
        if (this.nasRealPort != -1) {
            attributeList.add(new Attr_NASRealPort(this.nasRealPort));
        }
        if (this.nasPortType != -1) {
            attributeList.add(new Attr_NASPortType(this.nasPortType));
        }
        RadiusClient client = null;
        try {
            client = this.radiusClientFactory.newInstance();
            final AccessRequest request = new AccessRequest(client, attributeList);

            final RadiusAuthenticator authenticator = RadiusClient.getAuthProtocol(this.protocol.getName());
            authenticator.setupRequest(client, request);
            authenticator.processRequest(request);

            final net.jradius.packet.RadiusResponse response = client.sendReceive(request, this.retries);
            LOGGER.debug("RADIUS response from [{}]: [{}]", client.getRemoteInetAddress().getCanonicalHostName(), response.toString(true, true));
            if (response instanceof AccessReject) {
                LOGGER.error("Radius authentication attempt is rejected");
                return null;
            }
            final List<RadiusAttribute> attributes = response.getAttributes().getAttributeList();
            LOGGER.debug("Radius response code [{}] accepted with attributes [{}] and identifier [{}]", response.getCode(), attributes, response.getIdentifier());
            return new RadiusResponse(response.getCode(), response.getIdentifier(), attributes);

        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}
