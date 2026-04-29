#include "ShaderProgram.h"
#include "common/log.h"

ShaderProgram::~ShaderProgram() {
    release();
}

GLuint ShaderProgram::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint len = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &len);
        if (len > 0) {
            std::string log(len, '\0');
            glGetShaderInfoLog(shader, len, nullptr, log.data());
            LOGE("Shader compile error: %s", log.c_str());
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool ShaderProgram::build(const char* vertexSource, const char* fragmentSource) {
    GLuint vs = compileShader(GL_VERTEX_SHADER, vertexSource);
    if (!vs) return false;

    GLuint fs = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (!fs) {
        glDeleteShader(vs);
        return false;
    }

    program_ = glCreateProgram();
    glAttachShader(program_, vs);
    glAttachShader(program_, fs);
    glLinkProgram(program_);

    // Shaders can be deleted after linking
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint linked = 0;
    glGetProgramiv(program_, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint len = 0;
        glGetProgramiv(program_, GL_INFO_LOG_LENGTH, &len);
        if (len > 0) {
            std::string log(len, '\0');
            glGetProgramInfoLog(program_, len, nullptr, log.data());
            LOGE("Program link error: %s", log.c_str());
        }
        glDeleteProgram(program_);
        program_ = 0;
        return false;
    }
    return true;
}

void ShaderProgram::release() {
    if (program_) {
        glDeleteProgram(program_);
        program_ = 0;
    }
}

void ShaderProgram::use() const {
    glUseProgram(program_);
}

GLint ShaderProgram::getUniformLocation(const char* name) const {
    return glGetUniformLocation(program_, name);
}

void ShaderProgram::setFloat(const char* name, float value) const {
    glUniform1f(getUniformLocation(name), value);
}

void ShaderProgram::setInt(const char* name, int value) const {
    glUniform1i(getUniformLocation(name), value);
}

void ShaderProgram::setMatrix4(const char* name, const float* matrix) const {
    glUniformMatrix4fv(getUniformLocation(name), 1, GL_FALSE, matrix);
}
