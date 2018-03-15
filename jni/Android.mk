LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := a.out
LOCAL_SRC_FILES := a.c
LOCAL_CPPFLAGS := -Wall -fPIE 
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -fPIE -pie

include $(BUILD_EXECUTABLE) 
