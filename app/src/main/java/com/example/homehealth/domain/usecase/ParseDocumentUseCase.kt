package com.example.homehealth.domain.usecase

import com.example.homehealth.data.local.entity.MedicalDocument
import com.example.homehealth.domain.model.ParseResult
import com.example.homehealth.domain.repository.DocumentRepository
import javax.inject.Inject

/** 文档解析用例：调用解析服务并返回结构化结果 */
class ParseDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(document: MedicalDocument, documentType: String? = null): ParseResult =
        documentRepository.parseDocument(document, documentType)
}
