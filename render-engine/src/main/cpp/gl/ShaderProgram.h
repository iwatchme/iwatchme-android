#pragma once

#include <GLES3/gl3.h>
#include <string>

class ShaderProgram {
public:
    ShaderProgram() = default;
    ~ShaderProgram();

    bool build(const char* vertexSource, const char* fragmentSource);
    void release();

    void use() const;
    GLuint program() const { return program_; }

    // Uniform setters
    GLint getUniformLocation(const char* name) const;
    void setFloat(const char* name, float value) const;
    void setInt(const char* name, int value) const;
    void setMatrix4(const char* name, const float* matrix) const;

private:
    static GLuint compileShader(GLenum type, const char* source);

    GLuint program_ = 0;
};
