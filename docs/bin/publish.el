;; emacs --batch --load bin/publish.el --visit src/org/index.org --funcall org-publish-current-project

(require 'org-publish)

(let ((dir (if (buffer-file-name) (expand-file-name "../" (file-name-directory (buffer-file-name))) default-directory)))
  (setq org-publish-use-timestamps-flag nil
        org-publish-project-alist
        `(
          ("org"
           :base-directory ,(expand-file-name "src/org/" dir)
           :base-extension "org"
           :publishing-directory ,(expand-file-name "target/html/" dir)
           :recursive t
           :publishing-function org-publish-org-to-html
           :headline-levels 2 
           :author-info nil
           :email-info nil
           :creator-info nil
           :html-postamble nil
           :style "<link rel='stylesheet' type='text/css' href='css/stylesheet.css' />"
           )
          ("static"
           :base-directory ,(expand-file-name "src/org/" dir)
           :base-extension "css\\|js\\|png\\|jpg\\|gif\\|pdf\\|mp3\\|ogg\\|swf"
           :publishing-directory ,(expand-file-name "target/html/" dir)
           :recursive t
           :publishing-function org-publish-attachment
           )
          ("docs" :components ("org" "static"))
          )))

