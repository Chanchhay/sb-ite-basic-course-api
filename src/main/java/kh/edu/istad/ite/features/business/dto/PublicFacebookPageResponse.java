package kh.edu.istad.ite.features.business.dto;

/** What the public storefront page shows as a "Find us on Facebook" link — null fields when no Page is connected. */
public record PublicFacebookPageResponse(
        String pageName,
        String pageUrl
) {
}
