# R0 development image only. Inherit the upstream kernel, HALs, partitions and security policy.
$(call inherit-product, device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk)

PRODUCT_NAME := yokuli_cf_x86_64_phone
PRODUCT_MODEL := Yokuli OS Cuttlefish R0
PRODUCT_BRAND := Yokuli
# PRODUCT_DEVICE stays vsoc_x86_64_only so the upstream BoardConfig remains authoritative.
# This development product removes only Launcher3 through YokuliHome.overrides.
# SystemUI, Settings, PermissionController, provisioning and SELinux are retained.
PRODUCT_PACKAGES += YokuliHome
PRODUCT_COPY_FILES += \
    vendor/yokuli/yokuli-build.json:$(TARGET_COPY_OUT_PRODUCT)/etc/yokuli/build.json
