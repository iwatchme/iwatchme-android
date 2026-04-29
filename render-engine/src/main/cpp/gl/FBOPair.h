#pragma once

#include <GLES3/gl3.h>

class FBOPair {
public:
    FBOPair() = default;
    ~FBOPair();

    void init(int width, int height);
    void release();

    GLuint getWriteFBO() const   { return fbo_[current_]; }
    GLuint getWriteTex() const   { return tex_[current_]; }
    GLuint getReadTex() const    { return tex_[1 - current_]; }
    void swap()                  { current_ = 1 - current_; }

    int width() const  { return width_; }
    int height() const { return height_; }

private:
    GLuint fbo_[2] = {0, 0};
    GLuint tex_[2] = {0, 0};
    int current_ = 0;
    int width_ = 0;
    int height_ = 0;
    bool initialized_ = false;
};
