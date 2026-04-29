#include "FBOPair.h"
#include "common/log.h"

FBOPair::~FBOPair() {
    release();
}

void FBOPair::init(int width, int height) {
    if (initialized_) release();

    width_ = width;
    height_ = height;

    for (int i = 0; i < 2; i++) {
        // Create texture
        glGenTextures(1, &tex_[i]);
        glBindTexture(GL_TEXTURE_2D, tex_[i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Create FBO and attach texture
        glGenFramebuffers(1, &fbo_[i]);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo_[i]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, tex_[i], 0);

        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOGE("FBOPair: framebuffer %d incomplete: 0x%x", i, status);
        }
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    current_ = 0;
    initialized_ = true;

    LOGI("FBOPair: initialized %dx%d", width, height);
}

void FBOPair::release() {
    if (!initialized_) return;
    glDeleteFramebuffers(2, fbo_);
    glDeleteTextures(2, tex_);
    fbo_[0] = fbo_[1] = 0;
    tex_[0] = tex_[1] = 0;
    initialized_ = false;
}
