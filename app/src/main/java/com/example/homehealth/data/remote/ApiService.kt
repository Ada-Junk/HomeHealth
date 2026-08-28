package com.example.homehealth.data.remote

import com.example.homehealth.data.remote.dto.ParseDocumentRequest
import com.example.homehealth.data.remote.dto.ParseDocumentResponse
import com.example.homehealth.data.remote.dto.QARequest
import com.example.homehealth.data.remote.dto.QAResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** 远程解析 / 问答服务接口（对应开发文档第 5 节 API 设计） */
interface ApiService {

    @POST("api/documents/parse")
    suspend fun parseDocument(@Body request: ParseDocumentRequest): ParseDocumentResponse

    @POST("api/qa/ask")
    suspend fun askQuestion(@Body request: QARequest): QAResponse
}
