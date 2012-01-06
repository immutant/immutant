(require 'org-publish)

(setq org-publish-project-alist
      `(
        ("org"
         :base-directory ,(expand-file-name "../src/org/")
         :base-extension "org"
         :publishing-directory ,(expand-file-name "../target/")
         :recursive t
         :publishing-function org-publish-org-to-html
         :headline-levels 4             ; Just the default for this project.
         :author-info nil
         :email-info nil
         :creator-info nil
         :html-postamble nil
         )
        ("static"
         :base-directory ,(expand-file-name "../src/org/")
         :base-extension "css\\|js\\|png\\|jpg\\|gif\\|pdf\\|mp3\\|ogg\\|swf"
         :publishing-directory ,(expand-file-name "../target/")
         :recursive t
         :publishing-function org-publish-attachment
         )
        ("docs" :components ("org" "static"))
        ))
