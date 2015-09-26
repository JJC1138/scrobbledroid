# Introduction #

[Scrobble Droid](http://scrobbledroid.com/) defines a broadcast intent action that other developers can use to easily add Last.fm scrobbling support to other music-playing applications. Your app handles the music playback and just passes the metadata to Scrobble Droid, which handles the Last.fm account details, keeping track of when tracks should be scrobbled, doing the actual submissions, and handling errors.

# User Consent #

You should always offer your users an option of whether they want to scrobble the files they play using your application. The option should be something like "Send details of played tracks to Last.fm (requires that Scrobble Droid be installed)" and it should be disabled by default. It may help your users if the text "Scrobble Droid" is a clickable link to Scrobble Droid's Market entry: `market://search?q=pname:net.jjc1138.android.scrobbler`

# Intent Details #

The action for the intent is:
`net.jjc1138.android.scrobbler.action.MUSIC_STATUS`

The data is stored in the intent's 'extras'. The one required extra is:
| Name | Type | Content |
|:-----|:-----|:--------|
| `playing` | `boolean` | `true` if there is music playing and `false` if it is stopped or paused |

If the music is currently stopped or paused then that is all you need. Any other extras are ignored in that case.

If the music is currently playing then you need at least one more extra. If the music being played comes from the device's Audio MediaStore database then you just need to supply an extra with the track's ID:
| Name | Type | Content |
|:-----|:-----|:--------|
| `id` | `int` or `long` | The contents of the `_id` column for the track's MediaStore entry |

If the music playing is not something that was directly chosen by the user (e.g. a radio broadcast) then the `source` extra should be set:
| Name | Type | Content |
|:-----|:-----|:--------|
| `source` | `String` | "P" for user choice, "R" for radio, "E" for personalized recommendation service, "U" for unknown |

"P" will be used by default if this extra is not present.

If the music is not playing from the device's Audio MediaStore (NB: the Video MediaStore is not currently supported), then you have to specify the full metadata in the intent extras. These fields are derived from those described in [section 3.2 of the Last.fm Submissions Protocol v1.2.1](http://www.last.fm/api/submissions#3.2), and they have the same meanings:
| Name | Type | Required | Content |
|:-----|:-----|:---------|:--------|
| `artist` | `String` | yes      | Artist name |
| `track` | `String` | yes      | Track title |
| `secs` | `int` | only if `source` is "P" or not present | Length of track in seconds |
| `album` | `String` | no       | Album title |
| `tracknumber` | `int` | no       | Track number on album |
| `mb-trackid` | `String` | no       | [MusicBrainz Track ID](http://musicbrainz.org/doc/TrackID) |

# When To Send #

The action should be broadcast when music starts playing, when it is stopped or paused, and when a new track starts playing (either because the user changed it manually or because it is the next track in a playlist). Sending the broadcast more often than that (say, when the user seeks within a track) doesn't do any harm, as long as the details of the intent are always current.

# Example #

Here is a minimal example showing how to broadcast a playing track's details:
```
Intent i = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");

i.putExtra("playing", true);
i.putExtra("artist", "100% Cotton");
i.putExtra("track", "Does Anybody Know");
i.putExtra("secs", 120);

sendBroadcast(i);
```

# Comments #

I would be very interested to hear any questions or comments you have about this API. Please mail me at scrobbledroid at jjc1138 dot net.