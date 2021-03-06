package de.techdev.trackr.domain.employee.login;

import de.techdev.trackr.TransactionalIntegrationTest;
import de.techdev.trackr.core.security.SecurityConfiguration;
import de.techdev.trackr.domain.employee.Employee;
import de.techdev.trackr.domain.employee.EmployeeRepository;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.openid.OpenIDAttribute;
import org.springframework.security.openid.OpenIDAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.echocat.jomon.testing.BaseMatchers.isNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Moritz Schulze
 */
@ContextConfiguration(classes = SecurityConfiguration.class)
public class TrackrUserDetailsServiceIntegrationTest extends TransactionalIntegrationTest {

    @Autowired
    private CredentialDataOnDemand credentialDataOnDemand;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    private TrackrUserDetailsService trackrUserDetailsService;

    @Before
    public void setUp() throws Exception {
        trackrUserDetailsService = new TrackrUserDetailsService();
        trackrUserDetailsService.setCredentialRepository(credentialRepository);
        trackrUserDetailsService.setEmployeeRepository(employeeRepository);

        credentialDataOnDemand.init();
    }

    @Test
    public void loadUserDetails() throws Exception {
        Credential credential = credentialDataOnDemand.getRandomObject();
        credential.setEnabled(true);
        credentialRepository.save(credential);

        OpenIDAuthenticationToken tokenMock = getOpenIDAuthenticationTokenMock(credential);
        UserDetails userDetails = trackrUserDetailsService.loadUserDetails(tokenMock);
        assertThat(userDetails.getUsername(), is(credential.getEmail()));
        assertThat(userDetails.isEnabled(), is(true));
    }

    @Test(expected = UsernameNotFoundException.class)
    public void loadUserDetailsNotFound() throws Exception {
        OpenIDAuthenticationToken tokenMock = getOpenIDAuthenticationTokenMock("moritz.schulze@test.de");
        trackrUserDetailsService.loadUserDetails(tokenMock);
    }

    @Test(expected = UsernameNotFoundException.class)
    public void loadUserDetailsCreated() throws Exception {
        Credential credential = credentialDataOnDemand.getRandomObject();
        credential.setEnabled(false);
        credentialRepository.save(credential);

        OpenIDAuthenticationToken tokenMock = getOpenIDAuthenticationTokenMock(credential);
        trackrUserDetailsService.loadUserDetails(tokenMock);
    }

    @Test
    public void handleNullCredentialForNonTechdev() throws Exception {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("email", "moritz.schulze@test.de");
        String message = trackrUserDetailsService.handleNullCredential(attributes);
        assertThat(message, is(TrackrUserDetailsService.USER_NOT_FOUND));
    }

    @Test
    public void handleNullCredentialForTechdev() throws Exception {
        Map<String, String> attributes = new HashMap<>();
        String email = "max.mustermann@techdev.de";
        attributes.put("email", email);
        attributes.put("first", "Max");
        attributes.put("last", "Mustermann");
        String message = trackrUserDetailsService.handleNullCredential(attributes);
        assertThat(message, is(TrackrUserDetailsService.USER_CREATED));

        Credential credential = credentialRepository.findByEmail(email);
        assertThat(credential, isNotNull());
        assertThat(credential.getEnabled(), is(false));
        assertThat(credential.getEmail(), is(email));

        Employee employee = employeeRepository.findOne(credential.getId());
        assertThat(employee, isNotNull());
        assertThat(employee.getFirstName(), is("Max"));
        assertThat(employee.getLastName(), is("Mustermann"));
    }

    private OpenIDAuthenticationToken getOpenIDAuthenticationTokenMock(Credential credential) {
        return getOpenIDAuthenticationTokenMock(credential.getEmail());
    }

    private OpenIDAuthenticationToken getOpenIDAuthenticationTokenMock(String _email) {
        OpenIDAttribute email = new OpenIDAttribute("email", "type", asList(_email));
        List<OpenIDAttribute> attributes = asList(email);
        OpenIDAuthenticationToken tokenMock = mock(OpenIDAuthenticationToken.class);
        when(tokenMock.getAttributes()).thenReturn(attributes);
        return tokenMock;
    }
}
