package wiki.asaf.wikisayit.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import wiki.asaf.wikisayit.audio.AndroidAudioSource
import wiki.asaf.wikisayit.audio.AudioSource
import wiki.asaf.wikisayit.audio.RecordingEngine
import javax.inject.Singleton

/** Wires the audio recording engine (E4/s-7lo) into the app; [wiki.asaf.wikisayit.audio.RecordingFileStore]
 * needs no explicit binding since Hilt resolves its `@ApplicationContext` constructor param directly. */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    @Singleton
    fun provideAudioSource(): AudioSource = AndroidAudioSource()

    @Provides
    @Singleton
    fun provideRecordingEngine(audioSource: AudioSource): RecordingEngine = RecordingEngine(audioSource)
}
