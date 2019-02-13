/*!
 * Start Bootstrap - Creative Bootstrap Theme (http://startbootstrap.com)
 * Code licensed under the Apache License v2.0.
 * For details, see http://www.apache.org/licenses/LICENSE-2.0.
 */

(function ($) {
  "use strict"; // Start of use strict

  // jQuery for page scrolling feature - requires jQuery Easing plugin
  $('a.page-scroll').bind('click', function (event) {
    var $anchor = $(this);
    $('html, body').stop().animate({
      scrollTop: ($($anchor.attr('href')).offset().top - 50)
    }, 1250, 'easeInOutExpo');
    event.preventDefault();
  });

  // Highlight the top nav as scrolling occurs
  $('body').scrollspy({
    target: '.navbar-fixed-top',
    offset: 51
  });

// Floating label headings for the contact form
  $(function () {
    $("body").on("input propertychange", ".floating-label-form-group", function (e) {
      $(this).toggleClass("floating-label-form-group-with-value", !!$(e.target).val());
    }).on("focus", ".floating-label-form-group", function () {
      $(this).addClass("floating-label-form-group-with-focus");
    }).on("blur", ".floating-label-form-group", function () {
      $(this).removeClass("floating-label-form-group-with-focus");
    });
  });


  // Closes the Responsive Menu on Menu Item Click
  $('.navbar-collapse ul li a').click(function () {
    $('.navbar-toggle:visible').click();
  });

  // Fit Text Plugin for Main Header
  $("h1").fitText(
    1.2, {
      minFontSize: '35px',
      maxFontSize: '65px'
    }
  );

  // Offset for Main Navigation
  $('#mainNav').affix({
    offset: {
      top: 100
    }
  });

  /**
   * Login pane handler
   */
  $(function () {
    $('#a-login').on('click', function () {
      $('#pane-navigation').hide();
      $('#mainNav').addClass("login-open");
      $('#pane-login').show();
    });

    $('#pane-login-a-brand').on('click', function() {
      $('#pane-login').hide();
      $('#mainNav').removeClass("login-open");
      $('#pane-navigation').show();
    });

    $('.navbar-nav a').on('click', function() {
      $(".navbar-nav li").removeClass("active");
      $(this).parent('li').addClass("active");
    });

    $('#scroll-to-top').on('click', function() {
      $(".navbar-nav li").removeClass("active");
    });

  });

  /**
   * This uses waypoints to fire an event when the user scrolls down
   * to one of the augmented elements. It then adds an animation
   * to the element and disables the waypoint, because
   * we want them to fire only once.
   */
  $(document).ready(function() {
    $('.animated').waypoint(function() {
      var delay = $(this.element).data("animated-delay");
      var wp = this;
      var $elem = $(this.element);

      var trigger = function() {
        $elem.addClass($elem.data('animated'));
        wp.disable();
      };

      if (delay) {
        setTimeout(function() { trigger(); }, delay);
      } else {
        trigger();
      }
    }, {
      offset: 'bottom-in-view',
      triggerOnce: true
    });
  });


})(jQuery); // End of use strict
