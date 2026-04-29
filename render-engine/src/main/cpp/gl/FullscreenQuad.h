#pragma once

#include <GLES3/gl3.h>

class FullscreenQuad {
public:
    FullscreenQuad() = default;
    ~FullscreenQuad();

    void init();
    void release();
    void draw() const;

private:
    GLuint vao_ = 0;
    GLuint vbo_ = 0;
    bool initialized_ = false;
};
