#include "FullscreenQuad.h"

// Fullscreen quad: 2 triangles, each vertex has (x, y, u, v)
// Position: NDC coordinates (-1 to 1)
// TexCoord: UV coordinates (0 to 1)
static const float kQuadVertices[] = {
    // x,    y,    u,    v
    -1.0f, -1.0f, 0.0f, 0.0f,   // bottom-left
     1.0f, -1.0f, 1.0f, 0.0f,   // bottom-right
    -1.0f,  1.0f, 0.0f, 1.0f,   // top-left
     1.0f,  1.0f, 1.0f, 1.0f,   // top-right
};

FullscreenQuad::~FullscreenQuad() {
    release();
}

void FullscreenQuad::init() {
    if (initialized_) return;

    glGenVertexArrays(1, &vao_);
    glGenBuffers(1, &vbo_);

    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(kQuadVertices), kQuadVertices, GL_STATIC_DRAW);

    // Attribute 0: position (vec2)
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);

    // Attribute 1: texCoord (vec2)
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));

    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    initialized_ = true;
}

void FullscreenQuad::release() {
    if (!initialized_) return;
    glDeleteVertexArrays(1, &vao_);
    glDeleteBuffers(1, &vbo_);
    vao_ = 0;
    vbo_ = 0;
    initialized_ = false;
}

void FullscreenQuad::draw() const {
    glBindVertexArray(vao_);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);
}
