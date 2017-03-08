LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := libcrypto
LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libcrypto.a
include $(PREBUILT_STATIC_LIBRARY)


include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS += -std=c99
LOCAL_MODULE := amiitool

FILE_LIST := $(wildcard $(LOCAL_PATH)/*.c)
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)

#LOCAL_SRC_FILES := main.c
LOCAL_STATIC_LIBRARIES  := crypto
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)

