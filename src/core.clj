(ns core
  (:require
   [net.cgrand.enlive-html :as e]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def headers [:h1 :h2 :h3 :h4 :h5 :h6])

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
   (set (map vector headers))
   (fn [h]
     (if-let [id (get-in h [:attrs :id])]
       (assoc h :content
              [{:tag :a
                :attrs {:href (str "#" id)}
                :content (:content h)}])
       h))))

(defn promote-h-tags
  "Promote all headers to the header type above (h1 stays h1)"
  [nodes]
  (let [mapping (into {:h1 :h1}
                      (map (fn [[v k]] [k v])
                           (partition 2 1 headers)))]
    (e/transform
     nodes
     (set (map vector headers))
     (fn [h]
       (assoc h :tag (mapping (:tag h)))))))

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

(defn group-content-in-div
  "Apart from a TOC anda content div, most of the DR docs page
  is super flat, which makes it hard to extract a particular section.
  This uses specified tags as separators and recursively groups all the content below inside a <div>"
  [partitions content]
  (let [partitioned-content (partition-by-tag (first partitions) content)]
    (concat
     (first partitioned-content)
     (mapv (fn [content]
             {:tag :div
              :attrs (when-let [id (get-in (first content) [:attrs :id])]
                       {:id (str "section" id)
                        :class (str (name (first partitions)) "-container")})
              :content (group-content-in-div (rest partitions) content)})
           (rest partitioned-content)))))

(def all-sections
  ["section--welcome"
   "section--community"
   "section--book"
   "section--tutorial-video"
   "section--getting-started-tutorial"
   "section--starting-a-new-dragonruby-project"
   "section--deploying-to-itch-io"
   "section--deploying-to-mobile-devices"
   "section--dragonruby-s-philosophy"
   "section--frequently-asked-questions--comments--and-concerns"
   "section--recipies-"
   "section---runtime-"
   "section---args-state-"
   "section---args-inputs-"
   "section---args-outputs-"
   "section---args-easing-"
   "section---args-string-"
   "section---args-grid-"
   "section---audio-"
   "section---easing-"
   "section---outputs-"
   "section---solids-"
   "section---borders-"
   "section---sprites-"
   "section---labels-"
   "section---screenshots-"
   "section---mouse-"
   "section---openentity-"
   "section---array-"
   "section---kernel-"
   "section---geometry-"
   "section--source-code"])

(def api-docs-sections
  ["section--recipies-"
   "#section---runtime-"
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


(defonce url "http://docs.dragonruby.org.s3-website-us-east-1.amazonaws.com")
(defonce html-string (-> (slurp url)
                     (str/replace "<=" "&lt="))) ;; hackaround for dodgy html
(defonce html-res (e/html-resource (io/input-stream (.getBytes html-string))))

(def grouped-sections
  (->> (e/select html-res [:div#content])
       bring-back-language-ruby
       turn-header-into-self-link
       first
       :content
       (remove string?)
       (group-content-in-div headers)))

(defn render-page!
  ([page body]
   (render-page! {} page body))
  ([{:keys [highlight]
     :or {highlight true}}
    page body]
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
                 (when highlight
                   [:script "hljs.highlightAll();"])]
                [:body
                 body]]))))))))

(defn check-site
  "Ensure the top level structure of the site hasn't changed from what we expect"
  []
  (= all-sections
     (map (comp :id :attrs) grouped-sections)))

(defn render-site! []
  (render-page!
   "api/index"
   (e/select grouped-sections (into #{} (map (comp vector keyword) api-docs-sections))))

  (render-page!
   {:highlight false}
   "samples/index"
   (seq (promote-h-tags (e/select grouped-sections [:#section--source-code])))))

