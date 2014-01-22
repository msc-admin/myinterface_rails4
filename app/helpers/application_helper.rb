module ApplicationHelper
  def title(value)
    unless value.nil?
      @title = "#{value} | MysmilecentralRails4"      
    end
  end
end
