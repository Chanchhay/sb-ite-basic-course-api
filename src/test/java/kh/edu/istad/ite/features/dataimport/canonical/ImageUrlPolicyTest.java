package kh.edu.istad.ite.features.dataimport.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * An imported picture is an address we hand to every visitor of the shop.
 *
 * The file it came from was written by someone outside FluxiBiz, so the address
 * is treated as a claim rather than a fact. These tests pin the shape of that
 * distrust: anything not recognisably a public https image host is refused, and
 * refused with a sentence a shopkeeper can act on.
 */
class ImageUrlPolicyTest {

    private String codeFor(String url) {
        return ImageUrlPolicy.rejectionFor(url)
                .map(ImageUrlPolicy.Rejection::code)
                .orElse(null);
    }

    @Test
    void acceptsAnOrdinaryHostedPicture() {
        assertThat(ImageUrlPolicy.rejectionFor("https://cdn.example.com/items/mug.jpg")).isEmpty();
    }

    /** Query strings are how every signed CDN link carries its signature. */
    @Test
    void acceptsASignedCdnLink() {
        assertThat(ImageUrlPolicy.rejectionFor(
                "https://images.example.com/a/b.png?w=800&sig=abc123")).isEmpty();
    }

    @Test
    void hasNothingToSayAboutAnEmptyCell() {
        assertThat(ImageUrlPolicy.rejectionFor(null)).isEmpty();
        assertThat(ImageUrlPolicy.rejectionFor("  ")).isEmpty();
    }

    /**
     * Plain http is the one people write by accident, and it fails twice: the
     * browser blocks it on a secure storefront, so allowing it would buy a
     * blank space rather than a picture.
     */
    @Test
    void refusesPlainHttp() {
        assertThat(codeFor("http://cdn.example.com/mug.jpg")).isEqualTo("IMAGE_URL_NOT_HTTPS");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=",
            "file:///etc/passwd",
            "ftp://example.com/mug.jpg"
    })
    void refusesAnythingThatIsNotAWebPicture(String url) {
        assertThat(codeFor(url)).isIn("IMAGE_URL_UNSAFE_SCHEME", "IMAGE_URL_NOT_A_LINK");
    }

    /**
     * The whole point of the exercise. Our own servers can reach addresses a
     * shopper never could, so a file naming one is either a mistake or an
     * attempt to make us fetch something on someone's behalf.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost/mug.jpg",
            "https://127.0.0.1/mug.jpg",
            "https://10.0.0.5/mug.jpg",
            "https://192.168.1.10/mug.jpg",
            "https://169.254.169.254/latest/meta-data/",
            "https://[::1]/mug.jpg",
            "https://minio.internal/assets/mug.jpg",
            "https://nas.local/mug.jpg",
            "https://intranet/mug.jpg"
    })
    void refusesAddressesOnlyWeCanReach(String url) {
        assertThat(codeFor(url)).isIn("IMAGE_URL_PRIVATE_HOST", "IMAGE_URL_IP_ADDRESS");
    }

    /**
     * 2130706433 and 0x7f.0.0.1 are both 127.0.0.1 written to slip past a check
     * that only looks for dotted quads. Refusing numeric hosts outright means
     * we never have to enumerate the spellings.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://2130706433/mug.jpg",
            "https://8.8.8.8/mug.jpg"
    })
    void refusesNumericHostsHoweverTheyAreSpelled(String url) {
        assertThat(codeFor(url)).isEqualTo("IMAGE_URL_IP_ADDRESS");
    }

    /**
     * The hex spelling never even reaches the numeric check, because Java will
     * not parse it as a host at all. Asserted anyway: what matters is that it
     * is refused, and this records which of the two doors it goes out of.
     */
    @Test
    void refusesTheHexSpellingOfALoopbackAddress() {
        assertThat(codeFor("https://0x7f.0.0.1/mug.jpg")).isEqualTo("IMAGE_URL_NOT_A_LINK");
    }

    /** Either a leaked password, or a disguise for the host that follows it. */
    @Test
    void refusesCredentialsInTheAddress() {
        assertThat(codeFor("https://user:pass@cdn.example.com/mug.jpg"))
                .isEqualTo("IMAGE_URL_HAS_CREDENTIALS");
    }

    @Test
    void refusesSomethingThatIsNotAnAddressAtAll() {
        assertThat(codeFor("mug.jpg")).isEqualTo("IMAGE_URL_NOT_A_LINK");
        assertThat(codeFor("/assets/mug.jpg")).isEqualTo("IMAGE_URL_NOT_A_LINK");
        assertThat(codeFor("https://cdn example.com/mug.jpg")).isEqualTo("IMAGE_URL_NOT_A_LINK");
    }

    /**
     * The cap is the item column's, not an opinion of ours — a longer link
     * would pass the checking screen and then fail the commit, which is the
     * one place a shop can do nothing about it.
     */
    @Test
    void refusesALinkLongerThanTheColumnItGoesInto() {
        String tooLong = "https://cdn.example.com/" + "a".repeat(ImageUrlPolicy.MAX_LENGTH) + ".jpg";

        assertThat(codeFor(tooLong)).isEqualTo("IMAGE_URL_TOO_LONG");
    }

    /** Every refusal has to say what to do next, not just that something is wrong. */
    @Test
    void explainsItselfInWordsAShopkeeperCanActon() {
        assertThat(ImageUrlPolicy.rejectionFor("http://cdn.example.com/mug.jpg"))
                .get()
                .extracting(ImageUrlPolicy.Rejection::message)
                .asString()
                .contains("https");
    }
}
