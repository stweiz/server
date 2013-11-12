package org.osiam.web.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.osiam.helper.HttpClientHelper;
import org.osiam.helper.HttpClientRequestResult;
import org.osiam.resources.helper.UserDeserializer;
import org.osiam.resources.scim.Extension;
import org.osiam.resources.scim.MultiValuedAttribute;
import org.osiam.resources.scim.User;
import org.osiam.web.util.MailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.apache.http.HttpStatus.*;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

@Controller
@RequestMapping(value = "/register")
public class RegisterController {

    private static final Logger LOGGER = Logger.getLogger(RegisterController.class.getName());

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String internalScimExtensionUrn = "urn:scim:schemas:osiam:1.0:register";

    private HttpClientHelper httpClient;
    private ObjectMapper mapper;

    @Value("${osiam.web.registermail.from}")
    private String registermailFrom;
    @Value("${osiam.web.registermail.subject}")
    private String registermailSubject;
    @Value("${osiam.web.registermail.linkprefix}")
    private String registermailLinkPrefix;
    @Value("${osiam.web.authserver.url}")
    private String createUserUri;

    @Inject
    ServletContext context;

    private MailSender mailSender = new MailSender();

    public RegisterController() {
        httpClient = new HttpClientHelper();
        mapper = new ObjectMapper();
        SimpleModule userDeserializerModule = new SimpleModule("userDeserializerModule", new Version(1, 0, 0, null))
                .addDeserializer(User.class, new UserDeserializer(User.class));
        mapper.registerModule(userDeserializerModule);
    }

    /**
     * Generates a form with all needed fields for creating a new user.
     *
     * @param authorization
     * @return
     */
    @RequestMapping(method=RequestMethod.GET)
    public void index(@RequestHeader final String authorization, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        InputStream in = context.getResourceAsStream("/WEB-INF/registration/registration.html");
        IOUtils.copy(in, response.getOutputStream());
    }

    /**
     *
     * Creates a new User.
     *
     * Needs all data given by the 'index'-form. Saves the user in an inactivate-state. Sends an activation-email to
     * the registered email-address.
     * @param authorization
     * @return
     */
    @RequestMapping(value = "/create", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<String> create(@RequestHeader final String authorization, @RequestBody String body) {
        ResponseEntity res = null;
        try {
            User parsedUser = mapper.readValue(body, User.class);

            String foundEmail = null;
            for (MultiValuedAttribute email : parsedUser.getEmails()) {
                if (email.isPrimary()) {
                    foundEmail = (String) email.getValue();
                }
            }
            if (foundEmail == null) {
                LOGGER.log(Level.WARNING, "No primary email found!");
                res = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            } else {

                // generate Activation Token
                String activationToken = UUID.randomUUID().toString();
                Extension webRegisterExt = null;

                // Extension und Token zum User hinzufügen}
                User.Builder builder = new User.Builder(parsedUser);

                Map<String,String> fields = new HashMap<>();
                fields.put("activation_token", activationToken);
                builder.addExtension(internalScimExtensionUrn, new Extension(internalScimExtensionUrn, fields));
                parsedUser = builder.build();

                // Save user
                HttpClientRequestResult saveUserResponse = saveUser(parsedUser, authorization);
                if (saveUserResponse.getStatusCode() != 200) {
                    res = new ResponseEntity(HttpStatus.valueOf(saveUserResponse.getStatusCode()));
                } else {

                    // Send activation mail
                    MimeMessage msg = new MimeMessage(Session.getDefaultInstance(System.getProperties()));
                    msg.addFrom(InternetAddress.parse(registermailFrom));
                    msg.addRecipient(Message.RecipientType.TO, InternetAddress.parse(foundEmail)[0]);
                    msg.addHeader("Subject", MimeUtility.encodeText(registermailSubject));

                    // Mailcontent with $REGISTERLINK as placeholder
                    InputStream registerMailContentStream = context.getResourceAsStream("/WEB-INF/registration/registermail-content.txt");

                    if (registerMailContentStream == null) {
                        LOGGER.log(Level.SEVERE, "Cant open registermail-content.txt on classpath! Please configure!");
                        res = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                    } else {
                        String mailContent = IOUtils.toString(registerMailContentStream);
                        StringBuilder activateURL = new StringBuilder(registermailLinkPrefix);
                        activateURL.append("?user=").append(parsedUser.getName());
                        activateURL.append("&token=").append(activationToken);

                        mailContent.replace("$REGISTERLINK", activateURL);
                        msg.setContent(mailContent, "text/plain");

                        mailSender.sendMail(msg);

                        res = new ResponseEntity(HttpStatus.OK);
                    }
                }
            }
        } catch (IOException | MessagingException e) {
            LOGGER.log(Level.SEVERE, "Internal error", e);
            res = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return res;

    }

    private HttpClientRequestResult saveUser(User userToSave, String authorization) throws IOException {
        String userAsString = mapper.writeValueAsString(userToSave);
        HttpClientRequestResult response = httpClient.executeHttpPut(createUserUri, "name", userAsString, AUTHORIZATION, authorization);
        return response;
    }

    /**
     * Activates a created user.
     *
     * @param authorization
     * @param user
     * @param token
     * @return
     */
    @RequestMapping(value = "/activate", method = RequestMethod.GET)
    public ResponseEntity<String> activate(@RequestHeader final String authorization,
                            @RequestParam("user") final String user, @RequestParam("token") final String token) {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
}


