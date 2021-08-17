#include <jni.h>
#include <string.h>
#include <android/log.h>

#include "util.h"
#include "nfc3d/amiibo.h"
#include "nfc3d/keygen.h"

static nfc3d_amiibo_keys keys;

JNIEXPORT jint Java_com_hiddenramblings_tagmo_AmiiTool_setKeysUnfixed(JNIEnv * env, jobject this, jbyteArray keydata, jint dataLength) {
	if (sizeof(keys.data) != dataLength)
		return 0;
	
	(*env)->GetByteArrayRegion(env, keydata, 0, sizeof(keys.data), (void *)&(keys.data));
	return 1;
}

JNIEXPORT jint Java_com_hiddenramblings_tagmo_AmiiTool_setKeysFixed(JNIEnv * env, jobject this, jbyteArray keydata, jint dataLength) {
	if (sizeof(keys.tag) != dataLength)
		return 0;
	
	(*env)->GetByteArrayRegion(env, keydata, 0, sizeof(keys.tag), (void *)&(keys.tag));
	return 1;
}

JNIEXPORT jint Java_com_hiddenramblings_tagmo_AmiiTool_unpack(JNIEnv * env, jobject this, jbyteArray tag, jint dataLength, jbyteArray returnData, jint returnDataLength) {
	
	if (dataLength< NFC3D_AMIIBO_SIZE || returnDataLength< NFC3D_AMIIBO_SIZE || dataLength != returnDataLength)
		return 0;
	
	uint8_t original[dataLength];
	uint8_t modified[NFC3D_AMIIBO_SIZE];
	
	(*env)->GetByteArrayRegion(env, tag, 0, dataLength, (void *)&original);
	
	if (!nfc3d_amiibo_unpack(&keys, original, modified))
		return 0;
	
	jbyte* bufferPtr = (*env)->GetByteArrayElements(env, returnData, NULL);
	memcpy(bufferPtr, original, dataLength); //copy any extra data in source to destination
	memcpy(bufferPtr, modified, NFC3D_AMIIBO_SIZE);
	(*env)->ReleaseByteArrayElements(env, returnData, bufferPtr, 0);
	
	return 1;
}

JNIEXPORT jint Java_com_hiddenramblings_tagmo_AmiiTool_pack(JNIEnv * env, jobject this, jbyteArray tag, jint dataLength, jbyteArray returnData, jint returnDataLength) {
	
	if (dataLength< NFC3D_AMIIBO_SIZE || returnDataLength< NFC3D_AMIIBO_SIZE || dataLength != returnDataLength)
		return 0;
	
	uint8_t original[dataLength];
	uint8_t modified[NFC3D_AMIIBO_SIZE];
	
	(*env)->GetByteArrayRegion(env, tag, 0, dataLength, (void *)&original);
	
	nfc3d_amiibo_pack(&keys, original, modified);
	
	jbyte* bufferPtr = (*env)->GetByteArrayElements(env, returnData, NULL);
	memcpy(bufferPtr, original, dataLength); //copy any extra data in source to destination
	memcpy(bufferPtr, modified, NFC3D_AMIIBO_SIZE);
	(*env)->ReleaseByteArrayElements(env, returnData, bufferPtr, 0);
	
	return 1;
}
