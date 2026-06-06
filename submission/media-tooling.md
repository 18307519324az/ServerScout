# Local Media Tooling

Installed locally on drive D:

- FFmpeg: `D:\规划agent\tools\media-tools\node_modules\ffmpeg-static\ffmpeg.exe`
- FFprobe: `D:\规划agent\tools\media-tools\node_modules\ffprobe-static\bin\win32\x64\ffprobe.exe`
- Python TTS packages for execution: `D:\codex-temp\python-packages`
- Generated media workspace: `D:\codex-temp\serverscout-sync-video`

Final video output:

- `video/serverscout-ai-briefing-voiceover.mp4`

Verification:

- FFprobe detected MP4 container, H.264 video, AAC audio, 1280x720.
- FFmpeg full decode verification passed from start to end.
- Frame extraction verified subtitles at 3 seconds, 60 seconds, and 108 seconds.
