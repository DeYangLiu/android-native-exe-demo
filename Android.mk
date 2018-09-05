
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := NativeExeDemo

LOCAL_SRC_FILES := $(call all-java-files-under, src)

#LOCAL_JAVA_LIBRARIES :=  framework

#LOCAL_CERTIFICATE := platform


include $(BUILD_PACKAGE)



