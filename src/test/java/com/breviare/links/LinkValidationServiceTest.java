package com.breviare.links;

import com.breviare.blocklist.BlocklistRepository;
import com.breviare.common.BreviareException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkValidationServiceTest {

    @Mock
    private BlocklistRepository blocklistRepository;

    private LinkValidationService linkValidationService;

    @BeforeEach
    void setUp() {
        // Safe Browsing API key left blank so checkSafeBrowsing short-circuits and never touches RestClient.
        // "brvr.io" is an arbitrary stand-in, not the real breviare.base-url (breviare-iter.vercel.app in prod).
        linkValidationService = new LinkValidationService(
                RestClient.builder(), blocklistRepository, "", "https://brvr.io");
    }

    @Test
    void rejectsJavascriptScheme() {
        assertThatThrownBy(() -> linkValidationService.validate("javascript:alert(1)"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsDataScheme() {
        assertThatThrownBy(() -> linkValidationService.validate("data:text/html,<script>alert(1)</script>"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsFileScheme() {
        assertThatThrownBy(() -> linkValidationService.validate("file:///etc/passwd"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void acceptsHttpsScheme() {
        when(blocklistRepository.existsByDomain("example.com")).thenReturn(false);

        assertThatCode(() -> linkValidationService.validate("https://example.com/path"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDestinationOnOwnDomain() {
        assertThatThrownBy(() -> linkValidationService.validate("https://brvr.io/abc123"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsDestinationOnSubdomainOfOwnDomain() {
        assertThatThrownBy(() -> linkValidationService.validate("https://links.brvr.io/abc123"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void allowsUnrelatedDomainThatMerelyContainsOwnDomainAsSuffixOfALongerLabel() {
        // e.g. "notbrvr.io" must not be treated as a subdomain of "brvr.io"
        when(blocklistRepository.existsByDomain("notbrvr.io")).thenReturn(false);

        assertThatCode(() -> linkValidationService.validate("https://notbrvr.io/x"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDestinationOnBlocklist() {
        when(blocklistRepository.existsByDomain("malicious.example")).thenReturn(true);

        assertThatThrownBy(() -> linkValidationService.validate("https://malicious.example/phish"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("REJECTED");
    }

    @Test
    void blocklistCheckIsCaseInsensitiveAndIgnoresWwwPrefix() {
        when(blocklistRepository.existsByDomain("malicious.example")).thenReturn(true);

        assertThatThrownBy(() -> linkValidationService.validate("https://WWW.Malicious.Example/phish"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("REJECTED");
    }

    @Test
    void allowsDestinationNotOnBlocklist() {
        when(blocklistRepository.existsByDomain("safe.example")).thenReturn(false);

        assertThatCode(() -> linkValidationService.validate("https://safe.example/ok"))
                .doesNotThrowAnyException();
    }
}
