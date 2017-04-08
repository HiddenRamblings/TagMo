/*
 * (c) 2017      Marcos Del Sol Vives
 *
 * SPDX-License-Identifier: MIT
 */

#include <nfc3d/version.h>
#include <stdio.h>
#include "gitversion.h"

const char * nfc3d_version_fork() {
	// TODO: maybe this should go in another file?
	return "socram";
}

uint32_t nfc3d_version_commit() {
	return GIT_COMMIT_ID;
}

uint32_t nfc3d_version_build() {
	return GIT_COMMIT_COUNT;
}
