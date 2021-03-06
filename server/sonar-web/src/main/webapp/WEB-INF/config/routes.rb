ActionController::Routing::Routes.draw do |map|
  map.namespace :api do |api|
    api.resources :events, :only => [:index, :show, :create, :destroy]
    api.resources :user_properties, :only => [:index, :show, :create, :destroy], :requirements => { :id => /.*/ }
    api.resources :projects, :only => [:index], :requirements => { :id => /.*/ }
    api.resources :favourites, :only => [:index, :show, :create, :destroy], :requirements => { :id => /.*/ }
  end

  map.connect 'api', :controller => 'api/java_ws', :action => 'redirect_to_ws_listing'

  map.connect 'api/resoures', :controller => 'api/resources', :action => 'index'

  map.resources 'properties', :path_prefix => 'api', :controller => 'api/properties', :requirements => { :id => /.*/ }

  # page plugins
  map.connect 'plugins/configuration/:page', :controller => 'plugins/configuration', :action => 'index', :requirements => { :page => /.*/ }
  map.connect 'plugins/home/:page', :controller => 'plugins/home', :action => 'index', :requirements => { :page => /.*/ }
  map.connect 'plugins/resource/:id', :controller => 'plugins/resource', :action => 'index', :requirements => { :id => /.*/ }

  # Install the default route as the lowest priority.
  map.connect ':controller/:action/:id', :requirements => { :id => /.*/ }

end
