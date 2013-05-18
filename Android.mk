
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := MovementSensor

LOCAL_ROOT_DIR := $(shell (cd $(LOCAL_PATH); pwd))

LOCAL_SRC_FILES := \
		$(call all-java-files-under, src) \
		$(call all-Iaidl-files-under, aidl) 

LOCAL_AIDL_INCLUDES += \
    $(LOCAL_ROOT_DIR)/aidl


# List of static libraries to include in the package
#LOCAL_STATIC_JAVA_LIBRARIES := ls-vsms ls-fbsdk ls-signpost-common ls-signpost-core

LOCAL_CERTIFICATE := platform
#LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res


include $(BUILD_PACKAGE)

##################################################
include $(CLEAR_VARS)

# This will install the file in /data
# 
LOCAL_MODULE_PATH := /system/etc/vsensor.d

include $(BUILD_PREBUILD)
