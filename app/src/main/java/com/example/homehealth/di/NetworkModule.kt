package com.example.homehealth.di

import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.remote.ApiService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    /** 自建后端服务（LlmClient 走 OkHttpClient 动态地址，无需注册 Retrofit） */
    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendRetrofit(
        client: OkHttpClient,
        gson: Gson,
        prefs: SettingsPrefs
    ): Retrofit {
        val baseUrl = prefs.parseBaseUrl.ifBlank { "http://10.0.2.2:8000/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(@Named("backend") retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}
