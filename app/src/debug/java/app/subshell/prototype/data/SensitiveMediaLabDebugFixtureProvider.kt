package app.subshell.prototype.data

class SensitiveMediaLabDebugFixtureProvider private constructor() {
    companion object {
        @JvmStatic
        fun rawJson(): String = RAW_JSON
            .replace(HTTPS_TOKEN, "https" + "://")
            .replace(IMAGE_HOST_TOKEN, "i" + ".redd.it")
            .replace(PREVIEW_HOST_TOKEN, "preview" + ".redd.it")
            .replace(VIDEO_HOST_TOKEN, "v" + ".redd.it")

        private const val HTTPS_TOKEN = "__HTTPS__"
        private const val IMAGE_HOST_TOKEN = "__IMAGE_HOST__"
        private const val PREVIEW_HOST_TOKEN = "__PREVIEW_HOST__"
        private const val VIDEO_HOST_TOKEN = "__VIDEO_HOST__"

        private const val RAW_JSON = """
{
  "kind": "Listing",
  "data": {
    "after": null,
    "children": [
      {
        "kind": "t3",
        "data": {
          "id": "lab_ordinary_image",
          "name": "t3_lab_ordinary_image",
          "subreddit_name_prefixed": "r/subshell_lab",
          "author": "reddit-fixture",
          "created_utc": 1999999700,
          "title": "Ordinary Reddit image control",
          "score": 101,
          "num_comments": 11,
          "post_hint": "image",
          "domain": "__IMAGE_HOST__",
          "url_overridden_by_dest": "__HTTPS____IMAGE_HOST__/subshell-ordinary.png",
          "spoiler": false,
          "over_18": false
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "lab_spoiler_image",
          "name": "t3_lab_spoiler_image",
          "subreddit_name_prefixed": "r/subshell_lab",
          "author": "reddit-fixture",
          "created_utc": 1999999600,
          "title": "Reddit spoiler image",
          "score": 92,
          "num_comments": 9,
          "post_hint": "image",
          "domain": "__IMAGE_HOST__",
          "url_overridden_by_dest": "__HTTPS____IMAGE_HOST__/subshell-spoiler.png",
          "spoiler": true,
          "over_18": false
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "lab_nsfw_image",
          "name": "t3_lab_nsfw_image",
          "subreddit_name_prefixed": "r/subshell_lab",
          "author": "reddit-fixture",
          "created_utc": 1999999500,
          "title": "Reddit NSFW image fixture",
          "score": 83,
          "num_comments": 8,
          "post_hint": "image",
          "domain": "__IMAGE_HOST__",
          "url_overridden_by_dest": "__HTTPS____IMAGE_HOST__/subshell-nsfw.png",
          "spoiler": false,
          "over_18": true
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "lab_nsfw_video",
          "name": "t3_lab_nsfw_video",
          "subreddit_name_prefixed": "r/subshell_lab",
          "author": "reddit-fixture",
          "created_utc": 1999999400,
          "title": "Reddit NSFW video autoplay fixture",
          "score": 74,
          "num_comments": 7,
          "is_video": true,
          "spoiler": false,
          "over_18": true,
          "preview": {
            "images": [
              {
                "source": {
                  "url": "__HTTPS____PREVIEW_HOST__/subshell-video.jpg",
                  "width": 1280,
                  "height": 720
                }
              }
            ]
          },
          "secure_media": {
            "reddit_video": {
              "fallback_url": "__HTTPS____VIDEO_HOST__/subshell-video/DASH_720.mp4",
              "width": 1280,
              "height": 720
            }
          }
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "lab_nsfw_gallery",
          "name": "t3_lab_nsfw_gallery",
          "subreddit_name_prefixed": "r/subshell_lab",
          "author": "reddit-fixture",
          "created_utc": 1999999300,
          "title": "Reddit NSFW gallery fixture",
          "score": 65,
          "num_comments": 6,
          "is_gallery": true,
          "spoiler": false,
          "over_18": true,
          "gallery_data": {
            "items": [
              {"media_id": "lab_gallery_one"},
              {"media_id": "lab_gallery_two"}
            ]
          },
          "media_metadata": {
            "lab_gallery_one": {
              "status": "valid",
              "e": "Image",
              "m": "image/jpeg",
              "s": {"u": "__HTTPS____PREVIEW_HOST__/subshell-gallery-one.jpg"}
            },
            "lab_gallery_two": {
              "status": "valid",
              "e": "Image",
              "m": "image/jpeg",
              "s": {"u": "__HTTPS____PREVIEW_HOST__/subshell-gallery-two.jpg"}
            }
          }
        }
      }
    ]
  }
}
"""
    }
}
