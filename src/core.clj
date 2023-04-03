(ns core
  (:require
   [net.cgrand.enlive-html :as e]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defonce url "http://docs.dragonruby.org.s3-website-us-east-1.amazonaws.com")
(defonce html-string (-> (slurp url)
                     (str/replace "<=" "&lt="))) ;; hackaround for dodgy html
(defonce html-res (e/html-resource (io/input-stream (.getBytes html-string))))


(defn bring-back-language-ruby
  "The DR docs now only group code snippets in a <pre> tag,
  but we want <pre><code class='language-ruby' like the good old days>"
  [nodes]
  (e/transform
   nodes
   [:pre]
   (fn [pre]
     (assoc pre :content
            [{:tag :code
              :attrs {:class "language-ruby"}
              :content (:content pre)}]))))

(defn turn-header-into-self-link
  [nodes]
  (e/transform
   nodes
   #{[:h1] [:h2] [:h3]}
   (fn [h]
     (assoc h :content
            [{:tag :a
              :attrs {:href (str "#" (get-in h [:attrs :id]))}
              :content (:content h)}]))))

(defn promote-h-tags
  [nodes]
  (e/transform
   nodes
   #{[:h1] [:h2] [:h3] [:h4]}
   (fn [h]
     (assoc h :tag (case (:tag h)
                     :h1 :h1
                     :h2 :h1
                     :h3 :h2
                     :h4 :h3)))))

(defn partition-by-tag
  "Given a flat list of html tags, we want to group them based on some meaningful
  top level tag (usually a h tag)"
  [split-tag content]
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
  (mapv
    (fn [content]
      {:tag :div
       :attrs {:id (str "section" (get-in (first content) [:attrs :id]))}
       :content (let [h2-content (partition-by-tag :h2 content)]
                  (concat
                   (first h2-content)
                   (mapv (fn [content]
                           {:tag :div
                            :content (let [h3-content (partition-by-tag :h3 content)]
                                       (concat
                                        (first h3-content)
                                        (mapv (fn [content]
                                                {:tag :div
                                                 :content content})
                                              (rest h3-content))))})
                         (rest h2-content))))})
    (partition-by-tag :h1 content))
  )

(defn output-section-page!
  [page body]
  (let [path (str "site/" page ".html")]
    (io/make-parents path)
    (spit path
          (str
           "<!DOCTYPE html>"
           (str/join
            (e/emit*
             (e/html
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
  (->> (e/select html-res [:div#content])
       bring-back-language-ruby
       turn-header-into-self-link
       first
       :content
       (remove string?)
       group-h1-content-in-div))

(comment
  
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
  (map (comp :id :attrs) grouped-sections)
  (map :tag grouped-sections)
  (:content (second grouped-sections))

  (nth (:content (nth grouped-sections 12)) 3)
  (promote-h-tags (e/select (nth grouped-sections 12) [:h2]))
    
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
     (e/select grouped-sections [(keyword s)]))))

(output-section-page!
 "api/index"
 (e/select grouped-sections (into #{} (map (comp vector keyword)
                                                            api-docs-sections))))

(output-section-page!
 "recipies/index"
 (e/select grouped-sections [:#section--recipies-]))

(output-section-page!
 "samples/index"
 (e/select (promote-h-tags grouped-sections) [:#section--source-code]))
