package kh.edu.istad.ite.features.dataimport.canonical;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What FluxiBiz is willing to point a shopper's browser at.
 *
 * An imported picture is a link, not a file: the shop keeps hosting it and we
 * hand the address to every visitor who opens the item. That makes the address
 * itself the thing to be careful about, because it arrives from a spreadsheet
 * nobody here wrote.
 *
 * The rule is an allow-list, not a block-list. Anything this class cannot
 * positively recognise as a public https image host is refused, so a spelling
 * of an internal address that nobody thought of fails closed rather than open.
 */
public final class ImageUrlPolicy {

    /**
     * Matching the shortest column an image URL passes through, which is
     * {@code CreateItemRequest.imageUrl}. Catching it here costs a warning on
     * the checking screen; letting it past costs a failed commit the shop
     * cannot do anything about.
     */
    public static final int MAX_LENGTH = 255;

    /** Names that mean "this machine" or "this network", whatever the DNS says. */
    private static final Set<String> PRIVATE_SUFFIXES =
            Set.of(".localhost", ".local", ".internal", ".home.arpa", ".lan");

    /** Dotted quads, bare decimals like 2130706433, and 0x7f.0.0.1 alike. */
    private static final Pattern IP_LITERAL = Pattern.compile("^(\\[|[0-9.]+$|0[xX])");

    private ImageUrlPolicy() {
    }

    /** Why this address cannot be used, or empty if it can. */
    public record Rejection(String code, String message) {
    }

    public static Optional<Rejection> rejectionFor(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        if (url.length() > MAX_LENGTH) {
            return reject("IMAGE_URL_TOO_LONG",
                    "This image link is longer than " + MAX_LENGTH + " characters, which is more"
                            + " than an item can store.");
        }

        URI uri;

        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return notALink(url);
        }

        String scheme = uri.getScheme();

        if (scheme == null || uri.getHost() == null) {
            return notALink(url);
        }

        if (!scheme.equalsIgnoreCase("https")) {
            /*
             * Plain http is singled out because it is the one people write by
             * accident, and because it fails twice over: the browser blocks it
             * as mixed content on a secure storefront, so it would be a blank
             * space even if we allowed it.
             */
            return scheme.equalsIgnoreCase("http")
                    ? reject("IMAGE_URL_NOT_HTTPS",
                            "Image links have to start with https. A plain http picture is blocked"
                                    + " by the browser on a secure storefront, so it would not show"
                                    + " up anyway.")
                    : reject("IMAGE_URL_UNSAFE_SCHEME",
                            "\"" + scheme + ":\" links cannot be used as pictures.");
        }

        if (uri.getUserInfo() != null || uri.getAuthority().contains("@")) {
            /*
             * A username in the address is either a leaked password or an
             * attempt to make the part before the @ look like the real host.
             * Both are worth refusing outright.
             */
            return reject("IMAGE_URL_HAS_CREDENTIALS",
                    "This image link carries a username, which pictures never need.");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);

        if (IP_LITERAL.matcher(host).find()) {
            return reject("IMAGE_URL_IP_ADDRESS",
                    "This image link points at a numeric address rather than a website name."
                            + " Use the address the pictures are served from publicly.");
        }

        if (host.equals("localhost") || !host.contains(".")) {
            return privateHost();
        }

        for (String suffix : PRIVATE_SUFFIXES) {
            if (host.endsWith(suffix)) {
                return privateHost();
            }
        }

        return Optional.empty();
    }

    private static Optional<Rejection> notALink(String url) {
        return reject("IMAGE_URL_NOT_A_LINK",
                "\"" + url + "\" is not a full web address. An image link should look like"
                        + " https://example.com/photos/mug.jpg.");
    }

    private static Optional<Rejection> privateHost() {
        return reject("IMAGE_URL_PRIVATE_HOST",
                "This image link points inside a private network, so nobody visiting the shop"
                        + " would be able to see the picture.");
    }

    private static Optional<Rejection> reject(String code, String message) {
        return Optional.of(new Rejection(code, message));
    }
}
