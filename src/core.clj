(ns core
  (:require
   [net.cgrand.enlive-html :as html]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defonce url "http://docs.dragonruby.org.s3-website-us-east-1.amazonaws.com")
(defonce html-string (-> (slurp url)
                     (str/replace "<=" "&lt="))) ;; hackaround for dodgy html
(defonce html-res (html/html-resource (io/input-stream (.getBytes html-string))))


(defn bring-back-language-ruby
  "The DR docs now only group code snippets in a <pre> tag,
  but we want <pre><code class='language-ruby' like the good old days>"
  [nodes]
  (html/transform
   nodes
   [:pre]
   (fn [pre]
     (assoc pre :content
            [{:tag :code
              :attrs {:class "language-ruby"}
              :content (:content pre)}]))))

(defn partition-by-tag [split-tag content]
  (reduce (fn [ls node]
            (if (= (:tag node) split-tag)
              (conj ls [node])
              (conj (pop ls) (conj (peek ls) node))))
          [[]]
          content))

(defn group-h1-content-in-div
  "Apart from a TOC anda content div, most of the DR docs page
  is super flat, which makes it hard to extract a particular section.
  This uses <h1> tags as separators and groups all the content below inside a <div>"
  [content]
  (map
     (fn [content]
       {:tag :div
        :attrs {:id (str "section" (get-in (first content) [:attrs :id]))}
        :content (let [section-content (partition-by-tag :h2 content)]
                   (conj
                    (first section-content)
                    (rest (mapv (fn [c]
                                  {:tag :div
                                   :content c})
                                section-content))))})
     (partition-by-tag :h1 content)))

(defn output-section-page!
  [page body]
  (let [path (str "site/" page ".html")]
    (io/make-parents path)
    (spit path
          (str
           "<!DOCTYPE html>"
           (str/join
            (html/emit*
             (html/html
              [:html
               [:head
                [:link {:rel "stylesheet"
                        :href "/css/preflight.css"}]
                [:link {:rel "stylesheet"
                        :href "/css/style.css"}]
                [:link {:rel "stylesheet"
                        :href "/css/nord.min.css"}]
                [:script {:src "/js/highlight.min.js"}]
                [:script
                 "hljs.highlightAll();"]]
               [:body
                body]])))))))

(def grouped-sections
  (->> (html/select html-res [:div#content])
       bring-back-language-ruby
       first
       :content
       (remove string?)
       group-h1-content-in-div))

(comment
  (map (comp :id :attrs) grouped-sections)
         )

(def api-docs-sections
  ["#section---runtime-"
   "#section---args-state-"
   "#section---args-inputs-"
   "#section---args-outputs-"
   "#section---args-easing-"
   "#section---args-string-"
   "#section---args-grid-"
   "#section---audio-"
   "#section---easing-"
   "#section---outputs-"
   "#section---solids-"
   "#section---borders-"
   "#section---sprites-"
   "#section---labels-"
   "#section---screenshots-"
   "#section---mouse-"
   "#section---openentity-"
   "#section---array-"
   "#section---kernel-"
   "#section---geometry-"
])


(comment
  (-> "#section---geometry-"
      (str/replace-first "#section---" "")
      drop-last
      str/join)

  (doseq [s api-docs-sections]
    (output-section-page!
     (-> s
         (str/replace-first "#section---" "")
         drop-last
         str/join)
     (html/select grouped-sections [(keyword s)]))))

(output-section-page!
 "api/index"
 (html/select grouped-sections (into #{} (map (comp vector keyword)
                                              api-docs-sections))))

(output-section-page!
 "recipies/index"
 (html/select grouped-sections [:#section--recipies-]))
