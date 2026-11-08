using System;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Windows.Media.Control;

namespace EchoSmtcBridge
{
    internal static class Program
    {
        private static string _artworkPath = "";

        private static async Task Main(string[] args)
        {
            if (args.Length > 0) _artworkPath = args[0];

            Console.OutputEncoding = Encoding.UTF8;
            Console.InputEncoding = Encoding.UTF8;

            var manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
            if (manager == null)
            {
                Console.Error.WriteLine("[SMTCBridge] Could not request session manager");
                return;
            }

            var cmdThread = new Thread(() => CommandLoop(manager));
            cmdThread.IsBackground = true;
            cmdThread.Start();

            while (true)
            {
                try
                {
                    await EmitStatus(manager);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine("[SMTCBridge] status error: " + e.Message);
                }
                await Task.Delay(500);
            }
        }

        private static async Task EmitStatus(GlobalSystemMediaTransportControlsSessionManager manager)
        {
            var session = PickSession(manager);
            if (session == null)
            {
                Console.WriteLine("{}");
                Console.Out.Flush();
                return;
            }

            var props = await session.TryGetMediaPropertiesAsync();
            var playback = session.GetPlaybackInfo();
            var timeline = session.GetTimelineProperties();

            double position = 0.0;
            double duration = 0.0;
            if (timeline != null)
            {
                if (timeline.Position.TotalMilliseconds > 0) position = timeline.Position.TotalMilliseconds / 1000.0;
                if (timeline.EndTime.TotalMilliseconds > 0) duration = timeline.EndTime.TotalMilliseconds / 1000.0;
            }

            var payload = new
            {
                Title = props?.Title ?? "",
                Artist = props?.Artist ?? "",
                PositionSeconds = position,
                DurationSeconds = duration,
                ArtworkPath = await SaveArtworkAsync(props),
                IsPlaying = playback != null
                    && playback.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing,
                VolumePercent = 100,
                SourceApp = session.SourceAppUserModelId ?? ""
            };

            Console.WriteLine(JsonSerializer.Serialize(payload));
            Console.Out.Flush();
        }

        private static GlobalSystemMediaTransportControlsSession PickSession(GlobalSystemMediaTransportControlsSessionManager manager)
        {
            try
            {
                var sessions = manager.GetSessions();
                if (sessions == null || sessions.Count == 0) return null;

                foreach (var s in sessions)
                {
                    try
                    {
                        var info = s.GetPlaybackInfo();
                        if (info != null && info.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                            return s;
                    }
                    catch { }
                }
                foreach (var s in sessions)
                {
                    try
                    {
                        var info = s.GetPlaybackInfo();
                        if (info != null && info.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Paused)
                            return s;
                    }
                    catch { }
                }
                return null;
            }
            catch
            {
                return null;
            }
        }

        private static async Task<string> SaveArtworkAsync(GlobalSystemMediaTransportControlsSessionMediaProperties props)
        {
            if (string.IsNullOrEmpty(_artworkPath) || props == null || props.Thumbnail == null) return null;
            try
            {
                using var src = await props.Thumbnail.OpenReadAsync();
                using var dst = File.Create(_artworkPath);
                await src.AsStreamForRead().CopyToAsync(dst);
                return _artworkPath;
            }
            catch
            {
                return null;
            }
        }

        private static async void CommandLoop(GlobalSystemMediaTransportControlsSessionManager manager)
        {
            string line;
            while ((line = Console.ReadLine()) != null)
            {
                try
                {
                    line = line.Trim();
                    if (line.Length == 0) continue;
                    var session = PickSession(manager);
                    if (session == null) continue;

                    switch (line)
                    {
                        case "playpause":
                            await session.TryTogglePlayPauseAsync();
                            break;
                        case "next":
                            await session.TrySkipNextAsync();
                            break;
                        case "previous":
                            await session.TrySkipPreviousAsync();
                            break;
                    }
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine("[SMTCBridge] command error: " + e.Message);
                }
            }
        }
    }
}
