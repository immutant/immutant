require 'rubygems'
require 'nokogiri'
require 'fileutils'

PAGES =
  %w{ what-is
      installation
      deployment
      initialization
      web
      jobs
      messaging
      caching
      transactions
      daemons
      interactive
      jboss
      production }

TARGET_DIR = File.join( File.expand_path( File.dirname( __FILE__ ) ), "..", "target" )

class PostProcessor

  def initialize(pages, src_dir, output_dir)
    @pages = pages
    @src_dir = src_dir
    @output_dir = output_dir
    @index_toc = ""
  end

  def process
    @pages.each_with_index do |page, idx|
      chapter = idx + 1
      with_doc( "#{page}.html" ) do |doc|
        chapter_title = update_chapter_title( doc, chapter )
        clear_toc_header( doc ) 
        renumber_toc( doc, chapter )
        renumber_section_titles( doc, chapter )
        insert_prev_next_links( doc, chapter )
        
        @index_toc << extract_chapter_toc( doc, page, chapter_title )
      end
    end

    toc = Nokogiri::HTML::DocumentFragment.parse( %Q{<ul>#{@index_toc}</ul>} )    
    with_doc( 'index.html' ) do |doc|
      toc.parent = doc.at_css( '#index-toc' )
      insert_prev_next_links( doc, 0 )
      doc.css( 'li.previous' ).each(&:remove)
      doc.css( 'li.home' ).each(&:remove)
      # I'd rather have org-mode do this, but can't figure out how
      doc.at_css( '.releaseinfo').content = doc.at_css('#postamble .version').content
    end
  end

  def with_doc(filename)
    File.open( File.join( @src_dir, filename ) ) do |f|
      puts "Processing #{filename}..."
      doc = Nokogiri::HTML( f )
      
      yield doc
      
      FileUtils.mkdir_p( @output_dir ) unless File.exist?( @output_dir )
      File.open( File.join( @output_dir, filename ), 'w' ) do |f|
        f.write( doc.to_html )
      end
    end
  end
  
  def update_chapter_title(doc, chapter)
    header = doc.at_css( "#content .title" )
    header.content = "Chapter #{chapter}. #{header.content}"
  end
  
  def clear_toc_header(doc)
    doc.at_css( "#table-of-contents h2" ).remove
  end

  def renumber_toc(doc, chapter)
    toc( doc ).css( "a" ).each do |entry|
      content = entry.content.gsub(/(^[0-9.]+)/, '\1.')
      entry.content = "#{chapter}.#{content}"
    end
  end

  def renumber_section_titles(doc, chapter)
    (2..5).each do |level|
      doc.css( ".section-number-#{level}" ).each do |title_num|
        title_num.content = "#{chapter}.#{title_num.content}."
      end
    end
  end

  def toc(doc)
    doc.at_css( "#text-table-of-contents" )
  end

  def insert_prev_next_links(doc, chapter)
    prev_page = (chapter > 1 ? @pages[chapter - 2] : 'index')
    next_page = @pages[chapter]

    nav = doc.css( '.docnav' ).each do |nav|
      nav.at_css( '.previous a' )['href'] = "#{prev_page}.html"
      if next_page
        nav.at_css( '.next a' )['href'] = "#{next_page}.html"
      else
        nav.at_css( '.next' ).remove
      end
    end
  end
  

  def extract_chapter_toc(doc, page, title)
    fragment =  %Q{<li class="toc-chapter-heading"><a href="#{page}.html">#{title.gsub("Immutant ", '')}</a><ul>}
    toc( doc ).at_css( 'ul' ).children.each do |child|
      child.css( 'a' ).each { |anchor| anchor['href'] = "#{page}.html#{anchor['href']}" }
      fragment << child.to_html
    end
    fragment << "</ul></li>"
  end
  
end

PostProcessor.new( PAGES,
                   File.join( TARGET_DIR, "tmp" ),
                   File.join( TARGET_DIR, "html" ) ).process
