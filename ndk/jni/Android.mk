LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include $(LOCAL_PATH)/mbedtls/include
LOCAL_CFLAGS += -std=c99
LOCAL_MODULE := amiitool

FILE_LIST := $(wildcard $(LOCAL_PATH)/*.c) $(wildcard $(LOCAL_PATH)/mbedtls/*.c)
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)

#LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
