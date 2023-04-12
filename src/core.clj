(ns core
  (:require [babashka.pods :as pods]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(pods/load-pod "bootleg")
(require '[pod.retrogradeorbit.net.cgrand.enlive-html :as e])

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
       (assoc h
              :attrs (dissoc (:attrs h) :id)
              :content
              [{:tag :a
                :attrs {:id id
                        :class "inner-link"}}
               {:tag :a
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
   "section---numeric-"
   "section---kernel-"
   "section---geometry-"
   "section--source-code"
   ])

(def api-docs-sections
  ["section--recipies-"
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
   "section---numeric-"
   "section---kernel-"
   "section---geometry-"
   ])


(defn extract-toc-link [nodes tag]
  (let [ns (e/select nodes [tag])]
    (map (fn [n]
           [:a {:href (str "/api#" (-> (e/select n [:.inner-link]) first :attrs :id))}
            (str/join " " (map str/trim (e/select n [e/text-node])))])
         ns)))

(defn generate-toc
  [nodes]
  (let [sections (e/select nodes [:.h1-container])]
    [:div
     [:h1 "DragonRuby API Docs"]
     [:p "This content is generate from the " [:a {:href "http://docs.dragonruby.org.s3-website-us-east-1.amazonaws.com/"} "original DragonRuby docs"] ", but contains just the core API documentation with a few bells and whistles like sticky headers and syntax highlighting."]
     (map
      (fn [s1] (let [sections (e/select s1 [:.h2-container])]
                 (list [:h1 (extract-toc-link s1 :h1) ]
                       [:ul (map
                             (fn [s2]
                               [:li
                                (extract-toc-link s2 :h2)])
                             sections)])))
      sections)]))

(def source-path "temp/source.html")
(defn fetch-source! []
  (let [url "http://docs.dragonruby.org.s3-website-us-east-1.amazonaws.com"
        html-string (-> (slurp url) 
                        (str/replace "<=" "&lt="))] ;; hackaround for dodgy html
    (io/make-parents source-path)
    (spit source-path html-string)))

(defn render-page!
  ([page body]
   (render-page! {} page body))
  ([{:keys [highlight]
     :or {highlight true}}
    page body]
   (let [path (str "site" page "/index.html")]
     (io/make-parents path)
     (spit path
           (str
            "<!DOCTYPE html>"
            (str/join
             (e/emit*
              (e/html
               [:html
                [:head
                 [:title "DragonRuby Docs"]
                 [:link {:rel "canonical," :href "https://dragonruby.freeston.me/"}]
                 [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
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
  [grouped-sections]
  (= all-sections
     (map (comp :id :attrs) grouped-sections)))

(defn parse-grouped-sections [source-path]
  (let [html-res (e/html-resource source-path)]
    (->> (e/select html-res [:div#content])
             first
             :content
             (remove string?)
             (group-content-in-div headers)
             bring-back-language-ruby
             turn-header-into-self-link)))

(defn render-site! []
  (let [grouped-sections (parse-grouped-sections source-path)]
    (assert (check-site grouped-sections) "Site structure has changed - please adjust your expectations.")
    
    (fs/delete-tree "site")
    (fs/copy-tree "resources/static" "site")

    (let [api-sections (e/select grouped-sections (into #{} (map (comp vector keyword (partial str "#")) api-docs-sections)))]
      (render-page!
       ""
       (generate-toc api-sections))
      
      (render-page!
       "/api"
       api-sections))

    (render-page!
     {:highlight false}
     "/samples"
     (seq (promote-h-tags (e/select grouped-sections [:#section--source-code]))))))

(defn print-all-sections []
  (prn (map (comp :id :attrs) (parse-grouped-sections source-path))))

(defn -main []
  (if (fs/exists? source-path)
    (println "Source html already exits, skipping.")
    (do
      (println "Fetching source html...")
      (fetch-source!)))
  (println "Rendering site...")
  (render-site!))

