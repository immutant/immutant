;; emacs --batch --load bin/publish.el --visit src/org/index.org --funcall org-publish-current-project

(defvar immutant-version "${project.version}")

(defun create-postamble ()
  (concat "<div class=\"version\">" immutant-version "</div>"))


(let ((dir (if (buffer-file-name) (expand-file-name "../" (file-name-directory (buffer-file-name))) default-directory)))
  (setq load-path (cons (expand-file-name "elisp/" dir) load-path))
  (setq load-path (cons (expand-file-name "elisp/org-mode/lisp" dir) load-path))
  (setq load-path (cons (expand-file-name "elisp/org-mode/contrib/lisp" dir) load-path))

  (require 'clojure-mode)
  (require 'org-publish)

  (print (org-version))

  (setq org-publish-use-timestamps-flag nil
        org-export-html-style-include-default nil
        org-export-htmlize-output-type "css"
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
           :html-preamble "<p id=\"title\"><span class=\"site\"><strong>JBoss.org</strong></span><span class=\"doc\"><strong>Community Documentation</strong></span></p>"
           :html-postamble create-postamble
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
