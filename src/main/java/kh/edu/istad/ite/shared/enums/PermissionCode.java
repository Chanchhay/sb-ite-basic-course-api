package kh.edu.istad.ite.shared.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermissionCode {
    ADMIN_AUDIT_READ("admin-audit:read", "Admin Audit Read", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_BUSINESS_DELETE("admin-business:delete", "Admin Business Delete", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_BUSINESS_MANAGE("admin-business:manage", "Admin Business Manage", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_BUSINESS_READ("admin-business:read", "Admin Business Read", PermissionGroup.PLATFORM_ADMIN, false, true),

    ADMIN_CHANNEL_READ("admin-channel:read", "Admin Channel Read", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_CHANNEL_MANAGE("admin-channel:manage", "Admin Channel Manage", PermissionGroup.PLATFORM_ADMIN, false, true),

    ADMIN_PLATFORM_FEATURE_READ("admin-platform-feature:read", "Admin Platform Feature Read", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_PLATFORM_FEATURE_UPDATE("admin-platform-feature:update", "Admin Platform Feature Update", PermissionGroup.PLATFORM_ADMIN, false, true),

    ADMIN_CATEGORY_CREATE("admin-category:create", "Admin Category Create", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_CATEGORY_DELETE("admin-category:delete", "Admin Category Delete", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_CATEGORY_READ("admin-category:read", "Admin Category Read", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_CATEGORY_UPDATE("admin-category:update", "Admin Category Update", PermissionGroup.PLATFORM_ADMIN, false, true),
    
    ADMIN_DASHBOARD_READ("admin-dashboard:read", "Admin Dashboard Read", PermissionGroup.PLATFORM_ADMIN, false, true),
    
    ADMIN_UNIT_CREATE("admin-unit:create", "Admin Unit Create", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_UNIT_DELETE("admin-unit:delete", "Admin Unit Delete", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_UNIT_READ("admin-unit:read", "Admin Unit Read", PermissionGroup.PLATFORM_ADMIN, false, true),
    ADMIN_UNIT_UPDATE("admin-unit:update", "Admin Unit Update", PermissionGroup.PLATFORM_ADMIN, false, true),
    
    BAKONG_SETTING_PREVIEW("bakong-setting:preview", "Bakong Setting Preview", PermissionGroup.BUSINESS, true, false),
    BAKONG_SETTING_READ("bakong-setting:read", "Bakong Setting Read", PermissionGroup.BUSINESS, true, false),
    BAKONG_SETTING_UPDATE("bakong-setting:update", "Bakong Setting Update", PermissionGroup.BUSINESS, true, false),
    
    BUSINESS_CREATE("business:create", "Business Create", PermissionGroup.BUSINESS, false, false),
    BUSINESS_DELETE("business:delete", "Business Delete", PermissionGroup.BUSINESS, false, false),
    BUSINESS_READ("business:read", "Business Read", PermissionGroup.BUSINESS, true, false),
    BUSINESS_UPDATE("business:update", "Business Update", PermissionGroup.BUSINESS, true, false),
    
    CURRENCY_CREATE("currency:create", "Currency Create", PermissionGroup.CURRENCY, true, false),
    CURRENCY_DELETE("currency:delete", "Currency Delete", PermissionGroup.CURRENCY, true, false),
    CURRENCY_READ("currency:read", "Currency Read", PermissionGroup.CURRENCY, true, false),
    CURRENCY_SET_BASE("currency:set-base", "Currency Set Base", PermissionGroup.CURRENCY, true, false),
    CURRENCY_SET_DISPLAY("currency:set-display", "Currency Set Display", PermissionGroup.CURRENCY, true, false),
    CURRENCY_UPDATE("currency:update", "Currency Update", PermissionGroup.CURRENCY, true, false),
    
    ITEM_GROUP_CREATE("item-group:create", "Item Group Create", PermissionGroup.ITEM_GROUP, true, false),
    ITEM_GROUP_DELETE("item-group:delete", "Item Group Delete", PermissionGroup.ITEM_GROUP, true, false),
    ITEM_GROUP_READ("item-group:read", "Item Group Read", PermissionGroup.ITEM_GROUP, true, false),
    ITEM_GROUP_UPDATE("item-group:update", "Item Group Update", PermissionGroup.ITEM_GROUP, true, false),
    
    ITEM_CREATE("item:create", "Item Create", PermissionGroup.ITEM, true, false),
    ITEM_DELETE("item:delete", "Item Delete", PermissionGroup.ITEM, true, false),
    ITEM_READ("item:read", "Item Read", PermissionGroup.ITEM, true, false),
    ITEM_UPDATE("item:update", "Item Update", PermissionGroup.ITEM, true, false),
    
    MEMBER_MANAGE("member:manage", "Member Manage", PermissionGroup.MEMBER, true, false),
    MEMBER_READ("member:read", "Member Read", PermissionGroup.MEMBER, true, false),
    
    ORDER_CANCEL("order:cancel", "Order Cancel", PermissionGroup.ORDER, true, false),
    ORDER_CREATE("order:create", "Order Create", PermissionGroup.ORDER, true, false),
    ORDER_GENERATE_KHQR("order:generate-khqr", "Order Generate KHQR", PermissionGroup.ORDER, true, false),
    ORDER_PAY("order:pay", "Order Pay", PermissionGroup.ORDER, true, false),
    ORDER_READ("order:read", "Order Read", PermissionGroup.ORDER, true, false),
    
    PROFILE_READ("profile:read", "Profile Read", PermissionGroup.PROFILE, false, false),
    PROFILE_UPDATE("profile:update", "Profile Update", PermissionGroup.PROFILE, false, false),
    
    ROLE_ASSIGN("role:assign", "Role Assign", PermissionGroup.ROLE_MANAGEMENT, true, true),
    ROLE_CREATE("role:create", "Role Create", PermissionGroup.ROLE_MANAGEMENT, true, true),
    ROLE_DELETE("role:delete", "Role Delete", PermissionGroup.ROLE_MANAGEMENT, true, true),
    ROLE_READ("role:read", "Role Read", PermissionGroup.ROLE_MANAGEMENT, true, true),
    ROLE_UPDATE("role:update", "Role Update", PermissionGroup.ROLE_MANAGEMENT, true, true),
    
    STOCK_READ("stock:read", "Stock Read", PermissionGroup.STOCK, true, false),
    STOCK_WRITE("stock:write", "Stock Write", PermissionGroup.STOCK, true, false),
    
    STOREFRONT_READ("storefront:read", "Storefront Read", PermissionGroup.STOREFRONT, true, false),
    STOREFRONT_UPDATE("storefront:update", "Storefront Update", PermissionGroup.STOREFRONT, true, false),
    
    TELEGRAM_SETTING_READ("telegram-setting:read", "Telegram Setting Read", PermissionGroup.BUSINESS, true, false),
    TELEGRAM_SETTING_UPDATE("telegram-setting:update", "Telegram Setting Update", PermissionGroup.BUSINESS, true, false),
    
    UNIT_READ("unit:read", "Unit Read", PermissionGroup.ITEM, true, false);

    private final String code;
    private final String displayName;
    private final PermissionGroup group;
    private final boolean businessStaffAssignable;
    private final boolean platformStaffAssignable;
    
    public static PermissionCode fromCode(String code) {
        for (PermissionCode permissionCode : values()) {
            if (permissionCode.code.equals(code)) {
                return permissionCode;
            }
        }
        return null;
    }
}
