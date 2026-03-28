//
// Created by maks on 01.10.2022.
//

#include "IconLoader.h"

#include <jni.h>

#include <android/log.h>
#include "../ktx/ktx.h"
#include "../include/misc/visibility.h"
#include "../imgui/imgui_internal.h"
#include "Canvas/Canvas.h"

PRIVATE_API std::unordered_map<std::string, SkyImage*> IconLoader::images;
PRIVATE_API std::unordered_map<std::string, SkyImage*> IconLoader::atlas_images;
PRIVATE_API std::unordered_map<std::string, PrivateUIIcon*> icons;
PRIVATE_API struct AAssetManager* IconLoader::aAssetManager;

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_iconloader_IconLoader_addIcon(
        JNIEnv *env,
        jclass clazz,
        jstring name,
        jstring atlas_name,
        jfloat u0,
        jfloat v0,
        jfloat u1,
        jfloat v1
) {
    //since the icons will be loaded once at runtime, we can just hold JNI refs to them for eternity
    auto icon = new PrivateUIIcon;
    icon->uv0 = ImVec2(u0,v0);
    icon->uv1 = ImVec2(u1, v1);
    icon->atlasName =  (*env).GetStringUTFChars(atlas_name, nullptr);
    icon->atlasTexture = (ImTextureID)-2;
    icons.insert(std::make_pair(std::string((*env).GetStringUTFChars(name, nullptr)), icon));
}

PRIVATE_API SkyImage& die() {
    static SkyImage image;
    image.textureId = (ImTextureID)-1;
    return image;
}

PRIVATE_API SkyImage& IconLoader::uploadImageKtx(const char* name, const bool& is_atlas) {
    char* assetName = nullptr;
    asprintf(&assetName, "Data/Images/Bin/ETC2/%s.ktx", name);
    if(!assetName) return die();
    AAsset* aAsset = AAssetManager_open(Canvas::aAssetManager, assetName, AASSET_MODE_STREAMING);
    free(assetName);
    if(aAsset == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR,"IconLoader", "Image not found: %s", name);
        auto *image = new SkyImage;
        image->textureId = (ImTextureID)-1;
        IconLoader::images.insert(std::make_pair(name, image));
        return *image;
    }

    size_t assetSz = AAsset_getLength64(aAsset);
    void* assetBuf = malloc(assetSz);
    if(assetBuf == nullptr) { AAsset_close(aAsset); return die(); }
    if(AAsset_read(aAsset, assetBuf, assetSz) != assetSz) { free(assetBuf); AAsset_close(aAsset); return die(); }
    AAsset_close(aAsset);
    ktxTexture* texture = nullptr;
    GLuint texid = -1;
    GLenum target, error;
    if(ktxTexture_CreateFromMemory((const ktx_uint8_t *)assetBuf, assetSz, KTX_TEXTURE_CREATE_NO_FLAGS, &texture) != KTX_SUCCESS) { free(assetBuf); return die(); }
    glGenTextures(1, &texid);
    if(texid == -1) { free(assetBuf); ktxTexture_Destroy(texture); return die(); }
    auto *image = new SkyImage;
    error = glGetError();
    while(error != GL_NO_ERROR) {
        error = glGetError();
    }
    switch(ktxTexture_GLUpload(texture, &texid, &target, &error)) {
        case KTX_SUCCESS:
            free(assetBuf);
            image->size = ImVec2((float)texture->baseWidth, (float)texture->baseHeight);
            image->textureId = (ImTextureID)texid;
            if(is_atlas) {
                glBindTexture(target, texid);
                glTexParameteri(target, GL_TEXTURE_SWIZZLE_R, GL_RED);
                glTexParameteri(target, GL_TEXTURE_SWIZZLE_G, GL_RED);
                glTexParameteri(target, GL_TEXTURE_SWIZZLE_B, GL_RED);
                glTexParameteri(target, GL_TEXTURE_SWIZZLE_A, GL_RED);
                IconLoader::atlas_images.insert(std::make_pair(name, image));
            }else{
                IconLoader::images.insert(std::make_pair(name, image));
            }
            ktxTexture_Destroy(texture);
            return *image;
        case KTX_GL_ERROR:
            __android_log_print(ANDROID_LOG_ERROR,"IconLoader", "Failed to upload OpenGL texture: %i", error);
            free(assetBuf);
            glDeleteTextures(1, &texid);
            ktxTexture_Destroy(texture);
            image->textureId = IL_NO_TEXTURE;
            if(is_atlas) IconLoader::atlas_images.insert(std::make_pair(name, image));
            else IconLoader::images.insert(std::make_pair(name, image));
            return *image;
        case KTX_UNSUPPORTED_TEXTURE_TYPE:
            __android_log_print(ANDROID_LOG_ERROR,"IconLoader", "Unsupported texture type");
            free(assetBuf);
            glDeleteTextures(1, &texid);
            ktxTexture_Destroy(texture);
            image->textureId = IL_NO_TEXTURE;
            if(is_atlas) IconLoader::atlas_images.insert(std::make_pair(name, image));
            else IconLoader::images.insert(std::make_pair(name, image));
            return *image;
    }
}

PRIVATE_API PrivateUIIcon* IconLoader::getIcon(const std::string& icon) {
    auto iconi = icons.find(icon);
    if(iconi != icons.end()) {
        PrivateUIIcon* ricon = iconi->second;
        if(((long)ricon->atlasTexture) > 0) {
            return ricon;
        }else{
            if(ricon->atlasTexture == (ImTextureID)-2) {
                ricon->atlasTexture = IconLoader::getAtlasImage(ricon->atlasName).textureId;
            }
            if(ricon->atlasTexture == IL_NO_TEXTURE) {
                return nullptr;
            }
            return ricon;
        }
    }
    return nullptr;
}
PRIVATE_API void placeholder(const ImVec2& size2) {
    ImVec2 cursor = ImGui::GetCursorScreenPos();
    ImGui::Dummy(size2);
    ImGui::GetForegroundDrawList()->AddRectFilled(cursor, ImVec2(cursor.x + size2.x, cursor.y + size2.y), 0xFFFF00FF);
}

void IconLoader::icon(const std::string& name, const float& size, const ImVec4& color) {
    ImVec2 size2 = ImVec2(size, size);
    PrivateUIIcon* icon = getIcon(name);
    if(icon != nullptr) ImGui::Image((ImTextureID)icon->atlasTexture, size2, icon->uv0, icon->uv1, color);
    else placeholder(size2);
}

bool IconLoader::iconButton(const std::string &name, const float &size, const ImVec4 &color) {
    ImVec2 size2 = ImVec2(size, size);
    PrivateUIIcon *icon = getIcon(name);
    if (icon != nullptr) {
        return ImGui::ImageButtonEx(
                ImGui::GetCurrentWindow()->GetID(name.c_str()),
                (ImTextureID) icon->atlasTexture,
                size2,
                icon->uv0,
                icon->uv1,
                ImVec4(0, 0, 0, 0),
                color
        );
    }
    else return ImGui::Button(name.c_str(), size2);
}

PRIVATE_API SkyImage& IconLoader::getAtlasImage(const std::string &name)  {
    auto atlasi = IconLoader::atlas_images.find(name);
    if(atlasi != IconLoader::atlas_images.end()) {
        return *atlasi->second;
    }else{
        return uploadImageKtx(name.c_str(), true);
    }
}

SkyImage& IconLoader::getImage(const std::string &name) {
    auto atlasi = IconLoader::images.find(name);
    if(atlasi != IconLoader::images.end()) {
        return *atlasi->second;
    }else{
        return uploadImageKtx(name.c_str(), false);
    }
}

void IconLoader::getUIIcon(const std::string &name, UIIcon *publicIcon) {
    PrivateUIIcon* icon = getIcon(name);
    if(icon != nullptr) {
        publicIcon->textureId = icon->atlasTexture;
        publicIcon->uv0.x = icon->uv0.x;
        publicIcon->uv0.y = icon->uv0.y;
        publicIcon->uv1.x = icon->uv1.x;
        publicIcon->uv1.y = icon->uv1.y;
    }else{
        publicIcon->textureId = IL_NO_TEXTURE;
    }
}