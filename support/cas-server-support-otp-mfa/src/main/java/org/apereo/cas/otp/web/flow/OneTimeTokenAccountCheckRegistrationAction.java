package org.apereo.cas.otp.web.flow;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.OneTimeTokenAccount;
import org.apereo.cas.otp.repository.credentials.OneTimeTokenCredentialRepository;
import org.apereo.cas.util.QRUtils;
import org.apereo.cas.web.support.WebUtils;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.action.EventFactorySupport;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

/**
 * This is {@link OneTimeTokenAccountCheckRegistrationAction}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
@AllArgsConstructor
public class OneTimeTokenAccountCheckRegistrationAction extends AbstractAction {
    private final OneTimeTokenCredentialRepository repository;
    private final String label;
    private final String issuer;


    @Override
    protected Event doExecute(final RequestContext requestContext) {
        final String uid = WebUtils.getAuthentication(requestContext).getPrincipal().getId();

        final OneTimeTokenAccount acct = repository.get(uid);
        if (acct == null || StringUtils.isBlank(acct.getSecretKey())) {
            final OneTimeTokenAccount keyAccount = this.repository.create(uid);
            final String keyUri = "otpauth://totp/" + this.label + ':' + uid + "?secret=" + keyAccount.getSecretKey() + "&issuer=" + this.issuer;
            requestContext.getFlowScope().put("key", keyAccount);

            final String qrCodeBase64 = QRUtils.generateQRCode(keyUri, QRUtils.WIDTH_LARGE, QRUtils.WIDTH_LARGE);
            requestContext.getFlowScope().put("QRcode", qrCodeBase64);

            LOGGER.debug("Registration key URI is [{}]", keyUri);
            return new EventFactorySupport().event(this, "register");
        }
        return success();
    }
}
